package com.carddemo.batch.cbtrn02c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.AccountRecord;
import com.carddemo.batch.domain.CardXrefRecord;
import com.carddemo.batch.domain.DailyTranRecord;
import com.carddemo.batch.io.AccountStore;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.TcatbalStore;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Java migration of CBTRN02C — posts the daily transaction file. For each daily
 * transaction it validates (card xref, account, credit limit, expiration) and
 * either posts it (write TRANSACT, accumulate the transaction-category balance,
 * update the account) or writes a reject record.
 *
 * <p>Outputs (each compared byte-for-byte to the GnuCOBOL oracle):
 * {@code transact.dat} (TRANSACT KSDS dump, by tran-id), {@code tcatbaldump.dat}
 * (TCATBAL KSDS dump), {@code acctdump.dat} (ACCTFILE KSDS dump),
 * {@code rejects.dat} (DALYREJS sequential), {@code stdout.txt}.
 *
 * <p>Faithful-migration notes (oracle-verified): DALYTRAN is processed in file
 * order; the overlimit and expiration checks are sequential (a later failure
 * overwrites the reason); a posted transaction is the daily record with PROC-TS
 * replaced by CURRENT-DATE (masked in comparison) and a binary-zero FILLER; a
 * newly created TCATBAL key emits a "Creating." message.
 */
public final class Cbtrn02cMain {

    private static final int TEMP_BAL_INT_DIGITS = 9;   // WS-TEMP-BAL PIC S9(09)V99
    private static final DateTimeFormatter DB2_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS");

    private Cbtrn02cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        Map<String, CardXrefRecord> xrefByCard = new HashMap<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("cardxref.dat"), CardXrefRecord.WIDTH)) {
            CardXrefRecord x = CardXrefRecord.parse(r);
            xrefByCard.putIfAbsent(x.cardNum(), x);
        }
        AccountStore accounts = AccountStore.load(datasetDir.resolve("acctdata.dat"));
        TcatbalStore tcatbal = TcatbalStore.load(datasetDir.resolve("tcatbal.dat"));
        List<byte[]> dailyTrans = Cobol.readFixed(datasetDir.resolve("dailytran.dat"), DailyTranRecord.WIDTH);

        TreeMap<String, byte[]> transactByKey = new TreeMap<>();   // TRANSACT KSDS, keyed by tran-id
        ByteArrayOutputStream rejects = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        line(stdout, "START OF EXECUTION OF PROGRAM CBTRN02C");

        int tranCount = 0;
        int rejectCount = 0;
        for (byte[] raw : dailyTrans) {
            tranCount++;
            DailyTranRecord d = DailyTranRecord.parse(raw);

            int reason = 0;
            String desc = "";
            CardXrefRecord xref = xrefByCard.get(d.cardNum());
            if (xref == null) {
                reason = 100;
                desc = "INVALID CARD NUMBER FOUND";
            } else if (!accounts.contains(xref.acctId())) {
                reason = 101;
                desc = "ACCOUNT RECORD NOT FOUND";
            } else {
                AccountRecord acct = accounts.read(xref.acctId());
                BigDecimal tempBal = ZonedDecimal.truncateToField(
                        acct.cycleCredit().subtract(acct.cycleDebit()).add(d.amount()), TEMP_BAL_INT_DIGITS, 2);
                if (acct.creditLimit().compareTo(tempBal) < 0) {
                    reason = 102;
                    desc = "OVERLIMIT TRANSACTION";
                }
                if (acct.expirationDate().compareTo(d.origTs().substring(0, 10)) < 0) {
                    reason = 103;
                    desc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
                }
            }

            if (reason == 0) {
                if (!tcatbal.contains(xref.acctId(), d.typeCd(), d.catCd())) {
                    line(stdout, "TCATBAL record not found for key : "
                            + xref.acctId() + d.typeCd() + d.catCd() + ".. Creating.");
                }
                tcatbal.accumulate(xref.acctId(), d.typeCd(), d.catCd(), d.amount());
                accounts.applyTransaction(xref.acctId(), d.amount());
                transactByKey.put(d.id(), buildPostedTransaction(raw));
            } else {
                rejectCount++;
                rejects.writeBytes(raw);                                  // REJECT-TRAN-DATA X(350)
                rejects.writeBytes(String.format("%04d", reason).getBytes(Cobol.CHARSET));   // reason 9(4)
                byte[] descField = new byte[76];                          // desc X(76), space-padded
                Cobol.putText(descField, 0, 76, desc);
                rejects.writeBytes(descField);
            }
        }

        line(stdout, "TRANSACTIONS PROCESSED :" + String.format("%09d", tranCount));
        line(stdout, "TRANSACTIONS REJECTED  :" + String.format("%09d", rejectCount));
        line(stdout, "END OF EXECUTION OF PROGRAM CBTRN02C");

        ByteArrayOutputStream transact = new ByteArrayOutputStream();
        for (byte[] v : transactByKey.values()) {
            transact.writeBytes(v);
        }

        return new BatchOutputs.Builder()
                .add("transact.dat", transact.toByteArray())
                .add("tcatbaldump.dat", tcatbal.dump())
                .add("acctdump.dat", accounts.dump())
                .add("rejects.dat", rejects.toByteArray())
                .add("stdout.txt", stdout.toByteArray())
                .build();
    }

    /** A posted transaction is the daily record (id..orig-ts) with PROC-TS = CURRENT-DATE and 0x00 FILLER. */
    private static byte[] buildPostedTransaction(byte[] daily) {
        byte[] posted = new byte[DailyTranRecord.WIDTH];
        System.arraycopy(daily, 0, posted, 0, 304);          // ID .. ORIG-TS
        Cobol.putText(posted, 304, 26, LocalDateTime.now().format(DB2_TS) + "0000");   // PROC-TS (masked)
        // FILLER [330,350) stays 0x00
        return posted;
    }

    private static void line(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(Cobol.CHARSET));
        out.write('\n');
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        for (var e : result.files().entrySet()) {
            Files.write(Path.of(args[1]).resolve(e.getKey()), e.getValue());
        }
    }
}
