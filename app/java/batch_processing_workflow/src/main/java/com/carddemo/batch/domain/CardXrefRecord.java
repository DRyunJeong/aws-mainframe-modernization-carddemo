package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;

/**
 * Card cross-reference record — copybook {@code CVACT03Y} (RECLN 50).
 * CBACT04C reads this by ALTERNATE key (account-id) to obtain the card number
 * stamped on each generated interest transaction.
 *
 * <pre>
 *   05 XREF-CARD-NUM PIC X(16).  [ 0,16)   (primary key)
 *   05 XREF-CUST-ID  PIC 9(09).  [16,25)
 *   05 XREF-ACCT-ID  PIC 9(11).  [25,36)   (alternate key)
 *   05 FILLER        PIC X(14).  [36,50)
 * </pre>
 */
public record CardXrefRecord(String cardNum, String custId, String acctId) {

    public static final int WIDTH = 50;

    public static CardXrefRecord parse(byte[] r) {
        return new CardXrefRecord(
                Cobol.str(r, 0, 16),
                Cobol.str(r, 16, 9),
                Cobol.str(r, 25, 11));
    }
}
