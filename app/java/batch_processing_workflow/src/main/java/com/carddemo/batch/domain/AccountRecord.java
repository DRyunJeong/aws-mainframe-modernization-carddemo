package com.carddemo.batch.domain;

import com.carddemo.batch.io.Cobol;
import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;

/**
 * Account master record — copybook {@code CVACT01Y} (RECLN 300).
 *
 * <pre>
 *   05 ACCT-ID                PIC 9(11).      [  0, 11)
 *   05 ACCT-ACTIVE-STATUS     PIC X(01).      [ 11, 12)
 *   05 ACCT-CURR-BAL          PIC S9(10)V99.  [ 12, 24)   updated by interest
 *   05 ACCT-CREDIT-LIMIT      PIC S9(10)V99.  [ 24, 36)
 *   05 ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99.  [ 36, 48)
 *   05 ACCT-OPEN-DATE         PIC X(10).      [ 48, 58)
 *   05 ACCT-EXPIRAION-DATE    PIC X(10).      [ 58, 68)
 *   05 ACCT-REISSUE-DATE      PIC X(10).      [ 68, 78)
 *   05 ACCT-CURR-CYC-CREDIT   PIC S9(10)V99.  [ 78, 90)   zeroed on update
 *   05 ACCT-CURR-CYC-DEBIT    PIC S9(10)V99.  [ 90,102)   zeroed on update
 *   05 ACCT-ADDR-ZIP          PIC X(10).      [102,112)
 *   05 ACCT-GROUP-ID          PIC X(10).      [112,122)   disclosure-group key part
 *   05 FILLER                 PIC X(178).     [122,300)
 * </pre>
 *
 * <p>Backed by the raw 300-byte record image. CBACT04C reads the whole record
 * (alphanumeric move), changes only CURR-BAL / CYC-CREDIT / CYC-DEBIT, then
 * REWRITEs it — so every other field is passed through verbatim. Keeping the raw
 * image and rewriting only those three fields guarantees the untouched bytes are
 * preserved byte-for-byte, independent of any re-encoding.
 */
public final class AccountRecord {

    public static final int WIDTH = 300;
    public static final int AMT_SCALE = 2;
    public static final int AMT_INT_DIGITS = 10;          // S9(10)V99
    private static final int CURR_BAL_OFF = 12;
    private static final int CREDIT_LIMIT_OFF = 24;
    private static final int EXP_DATE_OFF = 58;
    private static final int CYC_CREDIT_OFF = 78;
    private static final int CYC_DEBIT_OFF = 90;
    private static final int AMT_LEN = 12;

    private final byte[] raw;

    public AccountRecord(byte[] raw) {
        if (raw.length != WIDTH) {
            throw new IllegalArgumentException("account record must be " + WIDTH + " bytes, got " + raw.length);
        }
        this.raw = raw.clone();
    }

    public String acctId() {
        return Cobol.str(raw, 0, 11);
    }

    public BigDecimal currBal() {
        return ZonedDecimal.decode(raw, CURR_BAL_OFF, AMT_LEN, AMT_SCALE);
    }

    public String activeStatus() {
        return Cobol.str(raw, 11, 1);
    }

    public BigDecimal creditLimit() {
        return ZonedDecimal.decode(raw, CREDIT_LIMIT_OFF, AMT_LEN, AMT_SCALE);
    }

    public BigDecimal cashCreditLimit() {
        return ZonedDecimal.decode(raw, 36, AMT_LEN, AMT_SCALE);
    }

    public String openDate() {
        return Cobol.str(raw, 48, 10);
    }

    public String reissueDate() {
        return Cobol.str(raw, 68, 10);
    }

    public BigDecimal cycleCredit() {
        return ZonedDecimal.decode(raw, CYC_CREDIT_OFF, AMT_LEN, AMT_SCALE);
    }

    public BigDecimal cycleDebit() {
        return ZonedDecimal.decode(raw, CYC_DEBIT_OFF, AMT_LEN, AMT_SCALE);
    }

    public String expirationDate() {
        return Cobol.str(raw, EXP_DATE_OFF, 10);
    }

    public String groupId() {
        return Cobol.str(raw, 112, 10);
    }

    /** A defensive copy of the raw 300-byte image. */
    public byte[] image() {
        return raw.clone();
    }

    /**
     * Produce the rewritten image for {@code 1050-UPDATE-ACCOUNT}: add accumulated
     * interest to CURR-BAL (truncated to the field) and zero the two cycle totals.
     * All other bytes are untouched.
     */
    public byte[] withPostedInterest(BigDecimal totalInterest) {
        byte[] out = raw.clone();
        BigDecimal newBal = ZonedDecimal.truncateToField(currBal().add(totalInterest), AMT_INT_DIGITS, AMT_SCALE);
        ZonedDecimal.encodeInto(out, CURR_BAL_OFF, newBal, AMT_LEN, AMT_SCALE);
        ZonedDecimal.encodeInto(out, CYC_CREDIT_OFF, BigDecimal.ZERO.setScale(AMT_SCALE), AMT_LEN, AMT_SCALE);
        ZonedDecimal.encodeInto(out, CYC_DEBIT_OFF, BigDecimal.ZERO.setScale(AMT_SCALE), AMT_LEN, AMT_SCALE);
        return out;
    }

    /**
     * Produce the rewritten image for CBTRN02C {@code 2800-UPDATE-ACCOUNT-REC}: add
     * the transaction amount to CURR-BAL, and to CYC-CREDIT (amt &ge; 0) or CYC-DEBIT
     * (amt &lt; 0). All other bytes are untouched.
     */
    public byte[] withPostedTransaction(BigDecimal amount) {
        byte[] out = raw.clone();
        ZonedDecimal.encodeInto(out, CURR_BAL_OFF,
                ZonedDecimal.truncateToField(currBal().add(amount), AMT_INT_DIGITS, AMT_SCALE), AMT_LEN, AMT_SCALE);
        if (amount.signum() >= 0) {
            ZonedDecimal.encodeInto(out, CYC_CREDIT_OFF,
                    ZonedDecimal.truncateToField(cycleCredit().add(amount), AMT_INT_DIGITS, AMT_SCALE), AMT_LEN, AMT_SCALE);
        } else {
            ZonedDecimal.encodeInto(out, CYC_DEBIT_OFF,
                    ZonedDecimal.truncateToField(cycleDebit().add(amount), AMT_INT_DIGITS, AMT_SCALE), AMT_LEN, AMT_SCALE);
        }
        return out;
    }
}
