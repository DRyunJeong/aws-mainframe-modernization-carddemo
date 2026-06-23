package com.carddemo.batch.cbtrn03c;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.domain.CardXrefRecord;
import com.carddemo.batch.domain.DailyTranRecord;
import com.carddemo.batch.domain.TranCatgRecord;
import com.carddemo.batch.domain.TranTypeRecord;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java migration of CBTRN03C — the transaction detail report. Reads the posted
 * transactions sequentially, filters by the DATEPARM date range, enriches each
 * with card/type/category lookups, and writes a formatted 133-byte report
 * (TRANREPT) with page/account/grand totals. Outputs: {@code report.dat} and
 * {@code stdout.txt}.
 *
 * <p>Faithful-migration notes (oracle-verified):
 * <ul>
 *   <li><b>EOF double-count</b>: at end-of-file the leftover {@code TRAN-AMT} (the
 *       last record's) is added <i>again</i> to the page/account totals before they
 *       are written.</li>
 *   <li><b>Last account total not written</b>: the final account's total line is
 *       only emitted on a card break, so the last card's account total is omitted.</li>
 *   <li><b>NEXT SENTENCE</b>: an out-of-range transaction terminates the loop (the
 *       {@code NEXT SENTENCE} jumps past {@code END-PERFORM}).</li>
 *   <li>Amounts use COBOL numeric editing PIC {@code -ZZZ,ZZZ,ZZZ.ZZ} (detail) /
 *       {@code +ZZZ,ZZZ,ZZZ.ZZ} (totals); page break every 20 lines.</li>
 * </ul>
 */
public final class Cbtrn03cMain {

    private static final int PAGE_SIZE = 20;
    private static final int TOTAL_INT_DIGITS = 9;          // WS-*-TOTAL PIC S9(09)V99

    private Cbtrn03cMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        Map<String, CardXrefRecord> xrefByCard = new HashMap<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("cardxref.dat"), CardXrefRecord.WIDTH)) {
            CardXrefRecord x = CardXrefRecord.parse(r);
            xrefByCard.putIfAbsent(x.cardNum(), x);
        }
        Map<String, TranTypeRecord> typeByKey = new HashMap<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("trantype.dat"), TranTypeRecord.WIDTH)) {
            TranTypeRecord t = TranTypeRecord.parse(r);
            typeByKey.putIfAbsent(t.typeCd(), t);
        }
        Map<String, TranCatgRecord> catgByKey = new HashMap<>();
        for (byte[] r : Cobol.readFixed(datasetDir.resolve("trancatg.dat"), TranCatgRecord.WIDTH)) {
            TranCatgRecord c = TranCatgRecord.parse(r);
            catgByKey.putIfAbsent(c.key(), c);
        }
        byte[] dateRec = Cobol.readFixed(datasetDir.resolve("dateparm.dat"), 80).get(0);
        String startDate = Cobol.str(dateRec, 0, 10);
        String endDate = Cobol.str(dateRec, 11, 10);
        List<byte[]> trans = Cobol.readFixed(datasetDir.resolve("transact.dat"), DailyTranRecord.WIDTH);

        ByteArrayOutputStream report = new ByteArrayOutputStream();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        line(stdout, "START OF EXECUTION OF PROGRAM CBTRN03C");
        line(stdout, "Reporting from " + startDate + " to " + endDate);

        State st = new State();
        byte[] current = new byte[DailyTranRecord.WIDTH];
        int idx = 0;
        boolean eof = false;
        while (!eof) {
            if (idx < trans.size()) {
                current = trans.get(idx++);
            } else {
                eof = true;
            }
            String procTs10 = Cobol.str(current, 304, 10);            // TRAN-PROC-TS(1:10)
            if (procTs10.compareTo(startDate) < 0 || procTs10.compareTo(endDate) > 0) {
                break;                                                // NEXT SENTENCE -> exit the loop
            }
            if (!eof) {
                stdout.writeBytes(current);                           // DISPLAY TRAN-RECORD
                stdout.write('\n');
                writeTransaction(report, current, st, xrefByCard, typeByKey, catgByKey, startDate, endDate);
            } else {
                BigDecimal amt = DailyTranRecord.parse(current).amount();   // leftover TRAN-AMT
                line(stdout, "TRAN-AMT " + Cobol.displaySigned(amt, 11));
                line(stdout, "WS-PAGE-TOTAL" + Cobol.displaySigned(st.pageTotal, 11));
                st.pageTotal = add(st.pageTotal, amt);                // EOF double-count
                st.accountTotal = add(st.accountTotal, amt);
                writePageTotals(report, st);
                writeGrandTotals(report, st);
            }
        }
        line(stdout, "END OF EXECUTION OF PROGRAM CBTRN03C");

        return new BatchOutputs.Builder()
                .add("report.dat", report.toByteArray())
                .add("stdout.txt", stdout.toByteArray())
                .build();
    }

    private static void writeTransaction(ByteArrayOutputStream report, byte[] raw, State st,
                                         Map<String, CardXrefRecord> xrefByCard,
                                         Map<String, TranTypeRecord> typeByKey,
                                         Map<String, TranCatgRecord> catgByKey,
                                         String startDate, String endDate) {
        DailyTranRecord t = DailyTranRecord.parse(raw);
        if (!st.currCard.equals(t.cardNum())) {
            if (!st.firstTime) {
                writeAccountTotals(report, st);
            }
            st.currCard = t.cardNum();
            st.currXref = xrefByCard.get(t.cardNum());        // 1500-A (abends if missing)
        }
        TranTypeRecord type = typeByKey.get(t.typeCd());
        TranCatgRecord catg = catgByKey.get(t.typeCd() + t.catCd());

        // 1100-WRITE-TRANSACTION-REPORT
        if (st.firstTime) {
            st.firstTime = false;
            writeHeaders(report, st, startDate, endDate);
        }
        if (st.lineCounter % PAGE_SIZE == 0) {
            writePageTotals(report, st);
            writeHeaders(report, st, startDate, endDate);
        }
        st.pageTotal = add(st.pageTotal, t.amount());
        st.accountTotal = add(st.accountTotal, t.amount());
        writeDetail(report, t, st.currXref, type, catg);
        st.lineCounter++;
    }

    // ---- report record builders (133-byte lines) ----

    private static void writeHeaders(ByteArrayOutputStream report, State st, String startDate, String endDate) {
        byte[] name = blank();
        Cobol.putText(name, 0, 38, "DALYREPT");
        Cobol.putText(name, 38, 41, "Daily Transaction Report");
        Cobol.putText(name, 79, 12, "Date Range: ");
        Cobol.putText(name, 91, 10, startDate);
        Cobol.putText(name, 101, 4, " to ");
        Cobol.putText(name, 105, 10, endDate);
        emit(report, name);
        st.lineCounter++;
        emit(report, blank());                                 // blank line
        st.lineCounter++;
        emit(report, header1());
        st.lineCounter++;
        emit(report, header2());
        st.lineCounter++;
    }

    private static void writeDetail(ByteArrayOutputStream report, DailyTranRecord t, CardXrefRecord xref,
                                    TranTypeRecord type, TranCatgRecord catg) {
        byte[] r = blank();
        Cobol.putText(r, 0, 16, t.id());
        Cobol.putText(r, 17, 11, xref.acctId());
        Cobol.putText(r, 29, 2, t.typeCd());
        r[31] = '-';
        Cobol.putText(r, 32, 15, type.description());
        Cobol.putText(r, 48, 4, t.catCd());
        r[52] = '-';
        Cobol.putText(r, 53, 29, catg.description());
        Cobol.putText(r, 83, 10, t.source());
        Cobol.putText(r, 97, 15, Cobol.editFloatingSign(t.amount(), ' '));
        emit(report, r);
    }

    private static void writePageTotals(ByteArrayOutputStream report, State st) {
        byte[] r = blank();
        Cobol.putText(r, 0, 11, "Page Total");
        dots(r, 11, 86);
        Cobol.putText(r, 97, 15, Cobol.editFloatingSign(st.pageTotal, '+'));
        emit(report, r);
        st.grandTotal = add(st.grandTotal, st.pageTotal);
        st.pageTotal = ZERO();
        st.lineCounter++;
        emit(report, header2());
        st.lineCounter++;
    }

    private static void writeAccountTotals(ByteArrayOutputStream report, State st) {
        byte[] r = blank();
        Cobol.putText(r, 0, 13, "Account Total");
        dots(r, 13, 84);
        Cobol.putText(r, 97, 15, Cobol.editFloatingSign(st.accountTotal, '+'));
        emit(report, r);
        st.accountTotal = ZERO();
        st.lineCounter++;
        emit(report, header2());
        st.lineCounter++;
    }

    private static void writeGrandTotals(ByteArrayOutputStream report, State st) {
        byte[] r = blank();
        Cobol.putText(r, 0, 11, "Grand Total");
        dots(r, 11, 86);
        Cobol.putText(r, 97, 15, Cobol.editFloatingSign(st.grandTotal, '+'));
        emit(report, r);
    }

    private static byte[] header1() {
        byte[] r = blank();
        Cobol.putText(r, 0, 17, "Transaction ID");
        Cobol.putText(r, 17, 12, "Account ID");
        Cobol.putText(r, 29, 19, "Transaction Type");
        Cobol.putText(r, 48, 35, "Tran Category");
        Cobol.putText(r, 83, 14, "Tran Source");
        Cobol.putText(r, 98, 16, "        Amount");
        return r;
    }

    private static byte[] header2() {
        byte[] r = new byte[133];
        java.util.Arrays.fill(r, (byte) '-');
        return r;
    }

    private static byte[] blank() {
        byte[] r = new byte[133];
        java.util.Arrays.fill(r, (byte) ' ');
        return r;
    }

    private static void dots(byte[] r, int off, int len) {
        java.util.Arrays.fill(r, off, off + len, (byte) '.');
    }

    private static void emit(ByteArrayOutputStream report, byte[] rec) {
        report.writeBytes(rec);                                // RECORD SEQUENTIAL: 133 bytes, no separator
    }

    private static BigDecimal add(BigDecimal a, BigDecimal b) {
        return ZonedDecimal.truncateToField(a.add(b), TOTAL_INT_DIGITS, 2);
    }

    private static BigDecimal ZERO() {
        return BigDecimal.ZERO.setScale(2);
    }

    private static void line(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(Cobol.CHARSET));
        out.write('\n');
    }

    private static final class State {
        boolean firstTime = true;
        long lineCounter = 0;
        BigDecimal pageTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal accountTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2);
        String currCard = " ".repeat(16);
        CardXrefRecord currXref;
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs result = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        for (var e : result.files().entrySet()) {
            Files.write(Path.of(args[1]).resolve(e.getKey()), e.getValue());
        }
    }
}
