package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;

/**
 * Transaction category record — copybook {@code CVTRA04Y} (RECLN 60).
 *
 * <pre>
 *   05 TRAN-CAT-KEY.
 *      10 TRAN-TYPE-CD       PIC X(02).  [ 0, 2)
 *      10 TRAN-CAT-CD        PIC 9(04).  [ 2, 6)
 *   05 TRAN-CAT-TYPE-DESC    PIC X(50).  [ 6,56)
 *   05 FILLER                PIC X(04).  [56,60)
 * </pre>
 */
public record TranCatgRecord(String typeCd, String catCd, String description) {

    public static final int WIDTH = 60;

    public static TranCatgRecord parse(byte[] r) {
        return new TranCatgRecord(Cobol.str(r, 0, 2), Cobol.str(r, 2, 4), Cobol.str(r, 6, 50));
    }

    /** Composite lookup key: type-cd(2) + cat-cd(4). */
    public String key() {
        return typeCd + catCd;
    }
}
