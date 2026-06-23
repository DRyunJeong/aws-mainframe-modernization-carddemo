package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Daily (unposted) transaction record — copybook {@code CVTRA06Y} (RECLN 350),
 * the input to CBTRN01C (verify) and CBTRN02C (post). Same layout as the posted
 * transaction (CVTRA05Y) but read from the sequential daily file.
 *
 * <pre>
 *   ID X(16)[0,16)  TYPE X(2)[16,18)  CAT 9(4)[18,22)  SOURCE X(10)[22,32)
 *   DESC X(100)[32,132)  AMT S9(09)V99[132,143)  MERCHANT-ID 9(9)[143,152)
 *   MERCHANT-NAME X(50)[152,202)  MERCHANT-CITY X(50)[202,252)  MERCHANT-ZIP X(10)[252,262)
 *   CARD-NUM X(16)[262,278)  ORIG-TS X(26)[278,304)  PROC-TS X(26)[304,330)  FILLER X(20)[330,350)
 * </pre>
 */
public record DailyTranRecord(String id, String typeCd, String catCd, String source, String desc,
                              BigDecimal amount, String merchantId, String merchantName,
                              String merchantCity, String merchantZip, String cardNum,
                              String origTs, String procTs) {

    public static final int WIDTH = 350;
    public static final int AMT_SCALE = 2;

    public static DailyTranRecord parse(byte[] r) {
        return new DailyTranRecord(
                Cobol.str(r, 0, 16), Cobol.str(r, 16, 2), Cobol.str(r, 18, 4), Cobol.str(r, 22, 10),
                Cobol.str(r, 32, 100), ZonedDecimal.decode(r, 132, 11, AMT_SCALE), Cobol.str(r, 143, 9),
                Cobol.str(r, 152, 50), Cobol.str(r, 202, 50), Cobol.str(r, 252, 10), Cobol.str(r, 262, 16),
                Cobol.str(r, 278, 26), Cobol.str(r, 304, 26));
    }
}
