package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;

/**
 * Customer master record — copybook {@code CVCUS01Y} (RECLN 500). All numeric
 * fields are unsigned (PIC 9), so no zoned-decimal sign handling is needed.
 *
 * <pre>
 *   CUST-ID 9(9)[0,9)  FIRST X(25)[9,34)  MIDDLE X(25)[34,59)  LAST X(25)[59,84)
 *   ADDR-1 X(50)[84,134)  ADDR-2 X(50)[134,184)  ADDR-3 X(50)[184,234)
 *   STATE X(2)[234,236)  COUNTRY X(3)[236,239)  ZIP X(10)[239,249)
 *   PHONE-1 X(15)[249,264)  PHONE-2 X(15)[264,279)  SSN 9(9)[279,288)
 *   GOVT-ID X(20)[288,308)  DOB X(10)[308,318)  EFT-ACCT X(10)[318,328)
 *   PRI-HOLDER X(1)[328,329)  FICO 9(3)[329,332)  FILLER X(168)[332,500)
 * </pre>
 */
public record CustomerRecord(String custId, String firstName, String middleName, String lastName,
                             String addrLine1, String addrLine2, String addrLine3,
                             String stateCd, String countryCd, String zip,
                             String phone1, String phone2, String ssn, String govtIssuedId,
                             String dob, String eftAccountId, String priCardHolderInd, String ficoScore) {

    public static final int WIDTH = 500;

    public static CustomerRecord parse(byte[] r) {
        return new CustomerRecord(
                Cobol.str(r, 0, 9), Cobol.str(r, 9, 25), Cobol.str(r, 34, 25), Cobol.str(r, 59, 25),
                Cobol.str(r, 84, 50), Cobol.str(r, 134, 50), Cobol.str(r, 184, 50),
                Cobol.str(r, 234, 2), Cobol.str(r, 236, 3), Cobol.str(r, 239, 10),
                Cobol.str(r, 249, 15), Cobol.str(r, 264, 15), Cobol.str(r, 279, 9), Cobol.str(r, 288, 20),
                Cobol.str(r, 308, 10), Cobol.str(r, 318, 10), Cobol.str(r, 328, 1), Cobol.str(r, 329, 3));
    }
}
