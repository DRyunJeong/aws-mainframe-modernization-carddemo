package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;

/**
 * Card master record — copybook {@code CVACT02Y} (RECLN 150).
 *
 * <pre>
 *   05 CARD-NUM            PIC X(16).  [  0, 16)   (record key)
 *   05 CARD-ACCT-ID        PIC 9(11).  [ 16, 27)
 *   05 CARD-CVV-CD         PIC 9(03).  [ 27, 30)
 *   05 CARD-EMBOSSED-NAME  PIC X(50).  [ 30, 80)
 *   05 CARD-EXPIRAION-DATE PIC X(10).  [ 80, 90)
 *   05 CARD-ACTIVE-STATUS  PIC X(01).  [ 90, 91)
 *   05 FILLER              PIC X(59).  [ 91,150)
 * </pre>
 */
public record CardRecord(String cardNum, String acctId, String cvv, String embossedName,
                         String expirationDate, String activeStatus) {

    public static final int WIDTH = 150;

    public static CardRecord parse(byte[] r) {
        return new CardRecord(
                Cobol.str(r, 0, 16),
                Cobol.str(r, 16, 11),
                Cobol.str(r, 27, 3),
                Cobol.str(r, 30, 50),
                Cobol.str(r, 80, 10),
                Cobol.str(r, 90, 1));
    }
}
