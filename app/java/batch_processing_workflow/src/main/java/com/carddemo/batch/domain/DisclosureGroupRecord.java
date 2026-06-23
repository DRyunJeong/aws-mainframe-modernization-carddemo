package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Disclosure group record — copybook {@code CVTRA02Y} (RECLN 50), the interest
 * rate master (DISCGRP).
 *
 * <pre>
 *   05 DIS-GROUP-KEY.
 *      10 DIS-ACCT-GROUP-ID PIC X(10).  [ 0,10)
 *      10 DIS-TRAN-TYPE-CD  PIC X(02).  [10,12)
 *      10 DIS-TRAN-CAT-CD   PIC 9(04).  [12,16)
 *   05 DIS-INT-RATE         PIC S9(04)V99.  [16,22)   annual rate as a percent (e.g. 15.00)
 *   05 FILLER               PIC X(28).  [22,50)
 * </pre>
 */
public record DisclosureGroupRecord(String groupId, String typeCd, String catCd, BigDecimal intRate) {

    public static final int WIDTH = 50;
    public static final int RATE_SCALE = 2;

    public static DisclosureGroupRecord parse(byte[] r) {
        return new DisclosureGroupRecord(
                Cobol.str(r, 0, 10),
                Cobol.str(r, 10, 2),
                Cobol.str(r, 12, 4),
                ZonedDecimal.decode(r, 16, 6, RATE_SCALE));
    }

    /** Composite lookup key: group-id(10) + type-cd(2) + cat-cd(4). */
    public String key() {
        return groupId + typeCd + catCd;
    }
}
