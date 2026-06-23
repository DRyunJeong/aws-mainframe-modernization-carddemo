package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Transaction Category Balance record — copybook {@code CVTRA01Y} (RECLN 50),
 * the driving file (TCATBALF) of the interest calculator.
 *
 * <pre>
 *   05 TRAN-CAT-KEY.
 *      10 TRANCAT-ACCT-ID  PIC 9(11).   [ 0,11)
 *      10 TRANCAT-TYPE-CD  PIC X(02).   [11,13)
 *      10 TRANCAT-CD       PIC 9(04).   [13,17)
 *   05 TRAN-CAT-BAL        PIC S9(09)V99.  [17,28)
 *   05 FILLER              PIC X(22).   [28,50)
 * </pre>
 */
public record TranCatBalRecord(String acctId, String typeCd, String catCd, BigDecimal balance) {

    public static final int WIDTH = 50;
    public static final int BAL_SCALE = 2;

    public static TranCatBalRecord parse(byte[] r) {
        return new TranCatBalRecord(
                Cobol.str(r, 0, 11),
                Cobol.str(r, 11, 2),
                Cobol.str(r, 13, 4),
                ZonedDecimal.decode(r, 17, 11, BAL_SCALE));
    }

    /** VSAM KSDS primary key (acct-id + type-cd + cat-cd); ISAM SEQUENTIAL read returns ascending key order. */
    public String key() {
        return acctId + typeCd + catCd;
    }
}
