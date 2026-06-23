package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;

/**
 * Transaction type record — copybook {@code CVTRA03Y} (RECLN 60).
 *
 * <pre>
 *   05 TRAN-TYPE       PIC X(02).  [ 0, 2)   (key)
 *   05 TRAN-TYPE-DESC  PIC X(50).  [ 2,52)
 *   05 FILLER          PIC X(08).  [52,60)
 * </pre>
 */
public record TranTypeRecord(String typeCd, String description) {

    public static final int WIDTH = 60;

    public static TranTypeRecord parse(byte[] r) {
        return new TranTypeRecord(Cobol.str(r, 0, 2), Cobol.str(r, 2, 50));
    }
}
