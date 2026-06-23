package com.carddemo.batch.cbact01c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.AccountRecord;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.Comp3;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Java migration of CBACT01C — reads the account master and writes three output
 * files plus SYSOUT, demonstrating mixed COBOL data formats:
 * <ul>
 *   <li>{@code outfile.dat} (107-byte fixed): DISPLAY fields + one COMP-3 field
 *       (CYC-DEBIT) + the reissue date reformatted by COBDATFT;</li>
 *   <li>{@code arryfile.dat} (110-byte fixed): an account id + a 5-element array of
 *       (DISPLAY balance, COMP-3 debit) with hard-coded demo values;</li>
 *   <li>{@code vbrcfile.dat} (variable length, RECORDING MODE V): two records per
 *       account (12 and 39 bytes), each prefixed by GnuCOBOL's 4-byte record
 *       descriptor (2-byte big-endian length + 2 zero bytes);</li>
 *   <li>{@code stdout.txt}: field-by-field DISPLAY + the two VBRC records + the
 *       raw ACCOUNT-RECORD.</li>
 * </ul>
 *
 * <p>Faithful-migration notes: the assembler date module {@code COBDATFT} is
 * reimplemented here as a YYYY-MM-DD&rarr;YYYYMMDD reformat (matching the local
 * COBDATFT.cbl stub used by the oracle); {@code CYC-DEBIT} of 0 is substituted with
 * 2525.00; element 3 and the OUTFILE/ARRYFILE COMP-3 fields exercise negative signs.
 */
public final class Cbact01cMain {

    private static final int AMT_DIGITS = 12;   // S9(10)V99
    private static final int AMT_SCALE = 2;

    private Cbact01cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        List<byte[]> records = Cobol.readFixed(datasetDir.resolve("acctdata.dat"), AccountRecord.WIDTH);
        // INDEXED SEQUENTIAL read returns ascending key (account-id) order.
        records.sort((a, b) -> Cobol.str(a, 0, 11).compareTo(Cobol.str(b, 0, 11)));

        ByteArrayOutputStream outfile = new ByteArrayOutputStream();
        ByteArrayOutputStream arryfile = new ByteArrayOutputStream();
        ByteArrayOutputStream vbrcfile = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        line(stdout, "START OF EXECUTION OF PROGRAM CBACT01C");

        // OUT-ACCT-CURR-CYC-DEBIT is only assigned when the input debit is zero
        // (-> 2525.00); otherwise the OUTFILE record area keeps its previous value.
        BigDecimal outCycDebit = BigDecimal.ZERO.setScale(2);
        for (byte[] raw : records) {
            AccountRecord a = new AccountRecord(raw);
            if (a.cycleDebit().signum() == 0) {
                outCycDebit = new BigDecimal("2525.00");
            }
            displayAccount(stdout, a);                              // 1100-DISPLAY-ACCT-RECORD
            outfile.writeBytes(buildOutfile(a, outCycDebit));      // 1300/1350
            arryfile.writeBytes(buildArrayfile(a));                // 1400/1450
            byte[] vb1 = buildVb1(a);
            byte[] vb2 = buildVb2(a);
            line(stdout, "VBRC-REC1:" + Cobol.str(vb1, 0, vb1.length));   // 1500
            line(stdout, "VBRC-REC2:" + Cobol.str(vb2, 0, vb2.length));
            writeVarRecord(vbrcfile, vb1);                         // 1550
            writeVarRecord(vbrcfile, vb2);                         // 1575
            stdout.writeBytes(raw);                                // DISPLAY ACCOUNT-RECORD (main loop)
            stdout.write('\n');
        }

        line(stdout, "END OF EXECUTION OF PROGRAM CBACT01C");
        return new BatchOutputs.Builder()
                .add("outfile.dat", outfile.toByteArray())
                .add("arryfile.dat", arryfile.toByteArray())
                .add("vbrcfile.dat", vbrcfile.toByteArray())
                .add("stdout.txt", stdout.toByteArray())
                .build();
    }

    private static byte[] buildOutfile(AccountRecord a, BigDecimal outCycDebit) {
        byte[] r = new byte[107];
        Cobol.putText(r, 0, 11, a.acctId());
        Cobol.putText(r, 11, 1, a.activeStatus());
        ZonedDecimal.encodeInto(r, 12, a.currBal(), AMT_DIGITS, AMT_SCALE);
        ZonedDecimal.encodeInto(r, 24, a.creditLimit(), AMT_DIGITS, AMT_SCALE);
        ZonedDecimal.encodeInto(r, 36, a.cashCreditLimit(), AMT_DIGITS, AMT_SCALE);
        Cobol.putText(r, 48, 10, a.openDate());
        Cobol.putText(r, 58, 10, a.expirationDate());
        Cobol.putText(r, 68, 10, cobdatft(a.reissueDate()));
        ZonedDecimal.encodeInto(r, 78, a.cycleCredit(), AMT_DIGITS, AMT_SCALE);
        Comp3.encodeInto(r, 90, outCycDebit, AMT_DIGITS, AMT_SCALE);    // leftover unless input debit was 0
        Cobol.putText(r, 97, 10, a.groupId());
        return r;
    }

    private static byte[] buildArrayfile(AccountRecord a) {
        byte[] r = new byte[110];
        Arrays.fill(r, (byte) ' ');                                // FILLER (INITIALIZE leaves it) = spaces
        Cobol.putText(r, 0, 11, a.acctId());
        putElem(r, 0, a.currBal(), new BigDecimal("1005.00"));
        putElem(r, 1, a.currBal(), new BigDecimal("1525.00"));
        putElem(r, 2, new BigDecimal("-1025.00"), new BigDecimal("-2500.00"));
        putElem(r, 3, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        putElem(r, 4, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        return r;
    }

    private static void putElem(byte[] r, int idx, BigDecimal bal, BigDecimal cycDebit) {
        int off = 11 + idx * 19;                                   // 12-byte DISPLAY bal + 7-byte COMP-3
        ZonedDecimal.encodeInto(r, off, bal, AMT_DIGITS, AMT_SCALE);
        Comp3.encodeInto(r, off + 12, cycDebit, AMT_DIGITS, AMT_SCALE);
    }

    private static byte[] buildVb1(AccountRecord a) {
        byte[] r = new byte[12];                                   // VB1-ACCT-ID 9(11) + STATUS X(1)
        Cobol.putText(r, 0, 11, a.acctId());
        Cobol.putText(r, 11, 1, a.activeStatus());
        return r;
    }

    private static byte[] buildVb2(AccountRecord a) {
        byte[] r = new byte[39];                                   // id + curr-bal + credit + reissue-yyyy
        Cobol.putText(r, 0, 11, a.acctId());
        ZonedDecimal.encodeInto(r, 11, a.currBal(), AMT_DIGITS, AMT_SCALE);
        ZonedDecimal.encodeInto(r, 23, a.creditLimit(), AMT_DIGITS, AMT_SCALE);
        Cobol.putText(r, 35, 4, a.reissueDate().substring(0, 4));  // WS-ACCT-REISSUE-YYYY
        return r;
    }

    /** GnuCOBOL RECORD VARYING (default COB_VARSEQ_FORMAT): 2-byte BE length + 2 zero bytes, then the record. */
    private static void writeVarRecord(ByteArrayOutputStream out, byte[] record) {
        out.write((record.length >> 8) & 0xFF);
        out.write(record.length & 0xFF);
        out.write(0);
        out.write(0);
        out.writeBytes(record);
    }

    private static void displayAccount(ByteArrayOutputStream out, AccountRecord a) {
        field(out, "ACCT-ID", a.acctId());
        field(out, "ACCT-ACTIVE-STATUS", a.activeStatus());
        field(out, "ACCT-CURR-BAL", Cobol.displaySigned(a.currBal(), AMT_DIGITS));
        field(out, "ACCT-CREDIT-LIMIT", Cobol.displaySigned(a.creditLimit(), AMT_DIGITS));
        field(out, "ACCT-CASH-CREDIT-LIMIT", Cobol.displaySigned(a.cashCreditLimit(), AMT_DIGITS));
        field(out, "ACCT-OPEN-DATE", a.openDate());
        field(out, "ACCT-EXPIRAION-DATE", a.expirationDate());
        field(out, "ACCT-REISSUE-DATE", a.reissueDate());
        field(out, "ACCT-CURR-CYC-CREDIT", Cobol.displaySigned(a.cycleCredit(), AMT_DIGITS));
        field(out, "ACCT-CURR-CYC-DEBIT", Cobol.displaySigned(a.cycleDebit(), AMT_DIGITS));
        field(out, "ACCT-GROUP-ID", a.groupId());
        line(out, "-".repeat(49));
    }

    /** "YYYY-MM-DD" -> "YYYYMMDD  " (the COBDATFT type-2->type-2 conversion). */
    private static String cobdatft(String yyyyMmDd) {
        return yyyyMmDd.substring(0, 4) + yyyyMmDd.substring(5, 7) + yyyyMmDd.substring(8, 10) + "  ";
    }

    private static void field(ByteArrayOutputStream out, String name, String value) {
        line(out, String.format("%-24s:", name) + value);
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
