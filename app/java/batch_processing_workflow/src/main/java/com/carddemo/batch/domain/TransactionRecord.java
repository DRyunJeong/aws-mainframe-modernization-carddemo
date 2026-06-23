package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generated interest transaction — copybook {@code CVTRA05Y} (RECLN 350),
 * written to TRANSACT by {@code 1300-B-WRITE-TX}.
 *
 * <p>This is a write-only builder. The byte layout and a few non-obvious
 * encodings were pinned against the GnuCOBOL oracle:
 * <ul>
 *   <li>The record image starts as binary zeros: fields set by the program are
 *       overwritten, but {@code TRAN-DESC} beyond the STRING and {@code FILLER}
 *       remain 0x00 (GnuCOBOL initialises working storage to LOW-VALUE here).</li>
 *   <li>{@code MOVE '05' TO TRAN-CAT-CD} (PIC 9(04)) yields {@code "0005"}.</li>
 *   <li>{@code MOVE 0 TO TRAN-MERCHANT-ID} (PIC 9(09)) yields {@code "000000000"}.</li>
 *   <li>{@code TRAN-ID} = PARM-DATE(10) + zero-padded 6-digit suffix (global,
 *       monotonically increasing across the whole run).</li>
 *   <li>{@code TRAN-ORIG-TS}/{@code TRAN-PROC-TS} come from FUNCTION CURRENT-DATE
 *       (non-deterministic) and are masked out before byte comparison.</li>
 * </ul>
 */
public final class TransactionRecord {

    public static final int WIDTH = 350;
    public static final int AMT_SCALE = 2;

    // 0-based field offsets within the 350-byte record.
    private static final int ID_OFF = 0, ID_LEN = 16;
    private static final int TYPE_OFF = 16;
    private static final int CAT_OFF = 18;
    private static final int SOURCE_OFF = 22;
    private static final int DESC_OFF = 32;
    private static final int AMT_OFF = 132, AMT_LEN = 11;
    private static final int MERCH_ID_OFF = 143;
    private static final int MERCH_NAME_OFF = 152;
    private static final int MERCH_CITY_OFF = 202;
    private static final int MERCH_ZIP_OFF = 252;
    private static final int CARD_OFF = 262, CARD_LEN = 16;
    /** TRAN-ORIG-TS start; the two 26-byte timestamps span [278, 330). */
    public static final int ORIG_TS_OFF = 278;
    private static final int PROC_TS_OFF = 304;
    public static final int TS_END = 330;

    private static final DateTimeFormatter DB2_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS");

    private TransactionRecord() {}

    /** Build one 350-byte interest transaction image. */
    public static byte[] build(String parmDate, int suffix, AccountRecord account,
                               String cardNumber, BigDecimal monthlyInterest) {
        byte[] r = new byte[WIDTH];                                   // LOW-VALUE (0x00) initialised
        Cobol.putText(r, ID_OFF, ID_LEN, parmDate + String.format("%06d", suffix));
        Cobol.putText(r, TYPE_OFF, 2, "01");
        Cobol.putText(r, CAT_OFF, 4, "0005");
        Cobol.putText(r, SOURCE_OFF, 10, "System");
        Cobol.putRaw(r, DESC_OFF, "Int. for a/c " + account.acctId());  // leaves rest of DESC as 0x00
        ZonedDecimal.encodeInto(r, AMT_OFF, monthlyInterest, AMT_LEN, AMT_SCALE);
        Cobol.putText(r, MERCH_ID_OFF, 9, "000000000");
        Cobol.putText(r, MERCH_NAME_OFF, 50, "");
        Cobol.putText(r, MERCH_CITY_OFF, 50, "");
        Cobol.putText(r, MERCH_ZIP_OFF, 10, "");
        Cobol.putText(r, CARD_OFF, CARD_LEN, cardNumber);
        String ts = LocalDateTime.now().format(DB2_TS) + "0000";       // masked in comparison
        Cobol.putText(r, ORIG_TS_OFF, 26, ts);
        Cobol.putText(r, PROC_TS_OFF, 26, ts);
        // FILLER [330,350) remains 0x00
        return r;
    }
}
