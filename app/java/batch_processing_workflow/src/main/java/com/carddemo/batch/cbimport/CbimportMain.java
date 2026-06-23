package com.carddemo.batch.cbimport;

import com.carddemo.batch.BatchOutputs;
import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.Comp;
import com.carddemo.batch.io.Comp3;
import com.carddemo.batch.io.ZonedDecimal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Java migration of CBIMPORT — reads the multi-record export file (CVEXPORT, 500-byte
 * fixed records, in sequence-number order) and splits it back into the five normalized
 * target files, routing on EXPORT-REC-TYPE. Binary/packed export fields are decoded back
 * to their DISPLAY form in the targets ({@link Comp}/{@link Comp3} &rarr; {@link ZonedDecimal});
 * alphanumeric and same-PIC DISPLAY fields are copied. Unknown record types are written to
 * an error file instead.
 *
 * <p>Faithful-migration notes (pinned by the GnuCOBOL oracle):
 * <ul>
 *   <li>{@code WRITE target-RECORD} writes the FD record area directly; {@code INITIALIZE}
 *       does not touch FILLER, and a GnuCOBOL output FD record area starts as low-values,
 *       so each target's trailing FILLER is {@code 0x00}.</li>
 *   <li>the error record is built in WORKING-STORAGE (130 bytes, '|'-delimited) and written
 *       {@code FROM} into a 132-byte FD, so the alphanumeric MOVE pads the last 2 bytes with
 *       spaces; its leading 26-byte ERR-TIMESTAMP is the wall-clock time and is masked.</li>
 * </ul>
 */
public final class CbimportMain {

    private static final int RECLEN = 500;       // CVEXPORT fixed record length
    private static final int DATA = 40;          // start of EXPORT-RECORD-DATA in the 500-byte record

    private final ByteArrayOutputStream cust = new ByteArrayOutputStream();
    private final ByteArrayOutputStream acct = new ByteArrayOutputStream();
    private final ByteArrayOutputStream xref = new ByteArrayOutputStream();
    private final ByteArrayOutputStream tran = new ByteArrayOutputStream();
    private final ByteArrayOutputStream card = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private CbimportMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        // EXPORT-INPUT is record-sequential 500-byte records carrying binary COMP fields
        // (a 0x0A inside a COMP field must not be read as a line break).
        List<byte[]> records = Cobol.readFixedRaw(datasetDir.resolve("expdata.dat"), RECLEN);
        records.sort(Comparator.comparing(r -> Comp.decode(r, 27, 9, 0, false)));   // by EXPORT-SEQUENCE-NUM

        CbimportMain job = new CbimportMain();
        for (byte[] r : records) {
            switch ((char) (r[0] & 0xFF)) {
                case 'C' -> job.customer(r);
                case 'A' -> job.account(r);
                case 'X' -> job.xref(r);
                case 'T' -> job.transaction(r);
                case 'D' -> job.card(r);
                default -> job.unknown(r);
            }
        }
        return new BatchOutputs.Builder()
                .add("custout.dat", job.cust.toByteArray())
                .add("acctout.dat", job.acct.toByteArray())
                .add("xrefout.dat", job.xref.toByteArray())
                .add("trnxout.dat", job.tran.toByteArray())
                .add("cardout.dat", job.card.toByteArray())
                .add("errout.dat", job.err.toByteArray())
                .build();
    }

    private void customer(byte[] e) {
        byte[] r = new byte[500];                                  // FD record area = low-values
        zonedU(r, 0, compU(e, DATA, 9), 9);                        // CUST-ID 9(09) <- EXP-CUST-ID COMP
        copy(e, DATA + 4, r, 9, 25 + 25 + 25 + 50 + 50 + 50 + 2 + 3 + 10 + 15 + 15);  // names..phones
        copy(e, DATA + 274, r, 279, 9);                            // CUST-SSN DISPLAY
        copy(e, DATA + 283, r, 288, 20 + 10 + 10 + 1);             // govt-id, dob, eft, pri-ind
        zonedU(r, 329, c3(e, DATA + 324, 3, 0), 3);                // CUST-FICO 9(03) <- COMP-3
        cust.writeBytes(r);
    }

    private void account(byte[] e) {
        byte[] r = new byte[300];
        copy(e, DATA, r, 0, 11 + 1);                               // ACCT-ID 9(11) + ACTIVE-STATUS
        zonedS(r, 12, c3(e, DATA + 12, 12, 2));                    // ACCT-CURR-BAL <- COMP-3
        copy(e, DATA + 19, r, 24, 12);                             // ACCT-CREDIT-LIMIT (DISPLAY->DISPLAY)
        zonedS(r, 36, c3(e, DATA + 31, 12, 2));                    // ACCT-CASH-CREDIT-LIMIT <- COMP-3
        copy(e, DATA + 38, r, 48, 10 + 10 + 10);                   // open / expiration / reissue dates
        copy(e, DATA + 68, r, 78, 12);                             // ACCT-CURR-CYC-CREDIT (DISPLAY->DISPLAY)
        zonedS(r, 90, compS(e, DATA + 80, 12, 2));                 // ACCT-CURR-CYC-DEBIT <- COMP (signed)
        copy(e, DATA + 88, r, 102, 10 + 10);                       // addr-zip, group-id
        acct.writeBytes(r);
    }

    private void xref(byte[] e) {
        byte[] r = new byte[50];
        copy(e, DATA, r, 0, 16);                                   // XREF-CARD-NUM
        copy(e, DATA + 16, r, 16, 9);                              // XREF-CUST-ID DISPLAY
        zonedU(r, 25, compU(e, DATA + 25, 11), 11);                // XREF-ACCT-ID 9(11) <- COMP
        xref.writeBytes(r);
    }

    private void transaction(byte[] e) {
        byte[] r = new byte[350];
        copy(e, DATA, r, 0, 16 + 2 + 4 + 10 + 100);                // tran-id, type, cat, source, desc
        zonedS(r, 132, c3(e, DATA + 132, 11, 2), 11);              // TRAN-AMT S9(09)V99 <- COMP-3
        zonedU(r, 143, compU(e, DATA + 138, 9), 9);               // TRAN-MERCHANT-ID 9(09) <- COMP
        copy(e, DATA + 142, r, 152, 50 + 50 + 10 + 16 + 26 + 26);  // name, city, zip, card, orig-ts, proc-ts
        tran.writeBytes(r);
    }

    private void card(byte[] e) {
        byte[] r = new byte[150];
        copy(e, DATA, r, 0, 16);                                   // CARD-NUM
        zonedU(r, 16, compU(e, DATA + 16, 11), 11);                // CARD-ACCT-ID 9(11) <- COMP
        zonedU(r, 27, compU(e, DATA + 24, 3), 3);                  // CARD-CVV-CD 9(03) <- COMP
        copy(e, DATA + 26, r, 30, 50 + 10 + 1);                    // embossed-name, expiration-date, active-status
        card.writeBytes(r);
    }

    /** WHEN OTHER: write a 132-byte '|'-delimited error record (leading ERR-TIMESTAMP masked). */
    private void unknown(byte[] e) {
        byte[] r = new byte[132];
        java.util.Arrays.fill(r, (byte) ' ');                      // WS-ERROR-RECORD spaces + WRITE-FROM pad
        // [0..25] ERR-TIMESTAMP (FUNCTION CURRENT-DATE) — masked by the golden
        r[26] = '|';
        r[27] = e[0];                                              // ERR-RECORD-TYPE
        r[28] = '|';
        ZonedDecimal.encodeInto(r, 29, Comp.decode(e, 27, 9, 0, false), 7, 0);  // ERR-SEQUENCE 9(07) <- seq COMP
        r[36] = '|';
        Cobol.putText(r, 37, 50, "Unknown record type encountered");           // ERR-MESSAGE
        err.writeBytes(r);
    }

    // ---- helpers ---------------------------------------------------------
    private static void copy(byte[] src, int sOff, byte[] dst, int dOff, int len) {
        System.arraycopy(src, sOff, dst, dOff, len);
    }

    private static BigDecimal compU(byte[] e, int off, int digits) {
        return Comp.decode(e, off, digits, 0, false);
    }

    private static BigDecimal compS(byte[] e, int off, int digits, int scale) {
        return Comp.decode(e, off, digits, scale, true);
    }

    private static BigDecimal c3(byte[] e, int off, int digits, int scale) {
        return Comp3.decode(e, off, digits, scale);
    }

    private static void zonedU(byte[] r, int off, BigDecimal v, int len) {
        ZonedDecimal.encodeInto(r, off, v, len, 0);
    }

    private static void zonedS(byte[] r, int off, BigDecimal v) {
        ZonedDecimal.encodeInto(r, off, v, 12, 2);
    }

    private static void zonedS(byte[] r, int off, BigDecimal v, int len) {
        ZonedDecimal.encodeInto(r, off, v, len, 2);
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs out = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        for (var en : out.files().entrySet()) {
            Files.write(Path.of(args[1]).resolve(en.getKey()), en.getValue());
        }
    }
}
