package com.carddemo.batch.cbexport;

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
 * Java migration of CBEXPORT — reads the five CardDemo master files (customer,
 * account, card cross-reference, transaction, card) in key order and writes a
 * single multi-record export file (CVEXPORT layout, 500-byte fixed records) for
 * branch migration. Records are emitted grouped by type in this order: customers
 * ('C'), accounts ('A'), xrefs ('X'), transactions ('T'), cards ('D'), with a
 * monotonic sequence number; the export file is keyed on that sequence so its
 * sequential image equals the write order.
 *
 * <p>Each 500-byte record is a common header — REC-TYPE X(1), TIMESTAMP X(26),
 * SEQUENCE-NUM 9(9) COMP, BRANCH-ID '0001', REGION-CODE 'NORTH' — followed by a
 * 460-byte type-specific payload (a REDEFINES view). {@code INITIALIZE} sets the
 * payload (an elementary PIC X(460)) to spaces, so unwritten gaps and trailing
 * FILLER are spaces. Numeric fields are re-encoded per the copybook: DISPLAY
 * &rarr; COMP (big-endian binary, {@link Comp}), DISPLAY &rarr; COMP-3 (packed,
 * {@link Comp3}), or DISPLAY &rarr; DISPLAY (zoned, value-preserving).
 *
 * <p>The 26-byte timestamp is the wall-clock run time (non-deterministic); the
 * golden masks it. The DISPLAY statistics (with the run date/time) are operational
 * logging, not a data product, so they are not part of the byte-for-byte golden;
 * the per-type counts are implied by the record counts in the export file.
 */
public final class CbexportMain {

    static final int RECLEN = 500;
    private static final int DATA = 40;           // start of EXPORT-RECORD-DATA
    private static final int AMT_SCALE = 2;

    private CbexportMain() {}

    public static BatchOutputs execute(Path datasetDir, String parmDate) throws IOException {
        List<byte[]> customers = readSorted(datasetDir, "custdata.dat", 500, 9);   // by CUST-ID
        List<byte[]> accounts = readSorted(datasetDir, "acctdata.dat", 300, 11);   // by ACCT-ID
        List<byte[]> xrefs = readSorted(datasetDir, "cardxref.dat", 50, 16);       // by XREF-CARD-NUM
        List<byte[]> transactions = readSorted(datasetDir, "trandata.dat", 350, 16); // by TRAN-ID
        List<byte[]> cards = readSorted(datasetDir, "carddata.dat", 150, 16);      // by CARD-NUM

        ByteArrayOutputStream exp = new ByteArrayOutputStream();
        int[] seq = {0};
        for (byte[] r : customers) {
            exp.writeBytes(customerRecord(r, ++seq[0]));
        }
        for (byte[] r : accounts) {
            exp.writeBytes(accountRecord(r, ++seq[0]));
        }
        for (byte[] r : xrefs) {
            exp.writeBytes(xrefRecord(r, ++seq[0]));
        }
        for (byte[] r : transactions) {
            exp.writeBytes(transactionRecord(r, ++seq[0]));
        }
        for (byte[] r : cards) {
            exp.writeBytes(cardRecord(r, ++seq[0]));
        }
        return new BatchOutputs.Builder().add("expfile.dat", exp.toByteArray()).build();
    }

    private static List<byte[]> readSorted(Path dir, String file, int width, int keyLen) throws IOException {
        List<byte[]> recs = Cobol.readFixed(dir.resolve(file), width);
        recs.sort(Comparator.comparing(r -> Cobol.str(r, 0, keyLen)));   // INDEXED SEQUENTIAL = ascending key
        return recs;
    }

    /** Common header: 'C/A/X/T/D' + 26 timestamp bytes (masked) + seq COMP + '0001' + 'NORTH'. */
    private static byte[] header(char type, int seq) {
        byte[] r = new byte[RECLEN];
        java.util.Arrays.fill(r, DATA, RECLEN, (byte) ' ');   // INITIALIZE sets the 460-byte data area to spaces
        r[0] = (byte) type;
        for (int i = 1; i <= 26; i++) {
            r[i] = ' ';                                       // WS-FORMATTED-TIMESTAMP (masked in golden)
        }
        Comp.encodeInto(r, 27, BigDecimal.valueOf(seq), 9, 0, false);   // EXPORT-SEQUENCE-NUM 9(9) COMP
        Cobol.putText(r, 31, 4, "0001");                      // EXPORT-BRANCH-ID
        Cobol.putText(r, 35, 5, "NORTH");                     // EXPORT-REGION-CODE
        return r;
    }

    private static byte[] customerRecord(byte[] s, int seq) {
        byte[] r = header('C', seq);
        comp(r, DATA, num(s, 0, 9), 9, 0, false);             // EXP-CUST-ID
        copy(s, 9, r, DATA + 4, 25 + 25 + 25 + 50 + 50 + 50 + 2 + 3 + 10 + 15 + 15);  // names..phones (contiguous X)
        copy(s, 279, r, DATA + 274, 9);                       // EXP-CUST-SSN (unsigned DISPLAY)
        copy(s, 288, r, DATA + 283, 20 + 10 + 10 + 1);        // govt-id, dob, eft, pri-ind
        comp3u(r, DATA + 324, num(s, 329, 3), 3, 0);          // EXP-CUST-FICO 9(03) COMP-3 (unsigned, 0xF sign)
        return r;
    }

    private static byte[] accountRecord(byte[] s, int seq) {
        byte[] r = header('A', seq);
        copy(s, 0, r, DATA, 11 + 1);                          // EXP-ACCT-ID 9(11) + ACTIVE-STATUS
        comp3(r, DATA + 12, money(s, 12), 12, 2);             // EXP-ACCT-CURR-BAL COMP-3
        zoned(r, DATA + 19, money(s, 24), 12);               // EXP-ACCT-CREDIT-LIMIT DISPLAY
        comp3(r, DATA + 31, money(s, 36), 12, 2);             // EXP-ACCT-CASH-CREDIT-LIMIT COMP-3
        copy(s, 48, r, DATA + 38, 10 + 10 + 10);              // open / expiration / reissue dates
        zoned(r, DATA + 68, money(s, 78), 12);               // EXP-ACCT-CURR-CYC-CREDIT DISPLAY
        comp(r, DATA + 80, money(s, 90), 12, 2, true);        // EXP-ACCT-CURR-CYC-DEBIT COMP (signed)
        copy(s, 102, r, DATA + 88, 10 + 10);                  // addr-zip, group-id
        return r;
    }

    private static byte[] xrefRecord(byte[] s, int seq) {
        byte[] r = header('X', seq);
        copy(s, 0, r, DATA, 16);                              // EXP-XREF-CARD-NUM
        copy(s, 16, r, DATA + 16, 9);                         // EXP-XREF-CUST-ID DISPLAY
        comp(r, DATA + 25, num(s, 25, 11), 11, 0, false);     // EXP-XREF-ACCT-ID COMP
        return r;
    }

    private static byte[] transactionRecord(byte[] s, int seq) {
        byte[] r = header('T', seq);
        copy(s, 0, r, DATA, 16 + 2 + 4 + 10 + 100);           // tran-id, type, cat(9(04)), source, desc
        comp3(r, DATA + 132, money(s, 132, 11), 11, 2);       // EXP-TRAN-AMT COMP-3 (S9(09)V99)
        comp(r, DATA + 138, num(s, 143, 9), 9, 0, false);     // EXP-TRAN-MERCHANT-ID COMP
        copy(s, 152, r, DATA + 142, 50 + 50 + 10 + 16 + 26 + 26);  // name, city, zip, card, orig-ts, proc-ts
        return r;
    }

    private static byte[] cardRecord(byte[] s, int seq) {
        byte[] r = header('D', seq);
        copy(s, 0, r, DATA, 16);                              // EXP-CARD-NUM
        comp(r, DATA + 16, num(s, 16, 11), 11, 0, false);     // EXP-CARD-ACCT-ID COMP
        comp(r, DATA + 24, num(s, 27, 3), 3, 0, false);       // EXP-CARD-CVV-CD COMP
        copy(s, 30, r, DATA + 26, 50 + 10 + 1);               // embossed-name, expiration-date, active-status
        return r;
    }

    // ---- field helpers ---------------------------------------------------
    private static void copy(byte[] src, int sOff, byte[] dst, int dOff, int len) {
        System.arraycopy(src, sOff, dst, dOff, len);
    }

    /** Decode an unsigned/signed integer DISPLAY field (scale 0). */
    private static BigDecimal num(byte[] s, int off, int len) {
        return ZonedDecimal.decode(s, off, len, 0);
    }

    /** Decode a signed money DISPLAY field S9(..)V99 (scale 2). */
    private static BigDecimal money(byte[] s, int off, int len) {
        return ZonedDecimal.decode(s, off, len, 2);
    }

    private static BigDecimal money(byte[] s, int off) {
        return money(s, off, 12);
    }

    private static void zoned(byte[] r, int off, BigDecimal v, int len) {
        ZonedDecimal.encodeInto(r, off, v, len, AMT_SCALE);
    }

    private static void comp(byte[] r, int off, BigDecimal v, int digits, int scale, boolean signed) {
        Comp.encodeInto(r, off, v, digits, scale, signed);
    }

    private static void comp3(byte[] r, int off, BigDecimal v, int digits, int scale) {
        Comp3.encodeInto(r, off, v, digits, scale);             // signed PIC (0xC/0xD)
    }

    private static void comp3u(byte[] r, int off, BigDecimal v, int digits, int scale) {
        Comp3.encodeInto(r, off, v, digits, scale, false);      // unsigned PIC (0xF)
    }

    public static void main(String[] args) throws IOException {
        BatchOutputs out = execute(Path.of(args[0]), "");
        Files.createDirectories(Path.of(args[1]));
        for (var e : out.files().entrySet()) {
            Files.write(Path.of(args[1]).resolve(e.getKey()), e.getValue());
        }
    }
}
