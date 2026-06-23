package com.carddemo.batch.io;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Codec for COBOL zoned-decimal {@code DISPLAY} numerics (PIC S9(n)V9(m)),
 * using GnuCOBOL's <b>native ASCII</b> sign convention — empirically pinned by
 * running the original CBACT04C under GnuCOBOL 3.2 (see src/test/cobol):
 *
 * <pre>
 *   every digit byte except the last : '0'..'9'  (0x30..0x39)
 *   last byte, positive value (incl 0): '0'..'9'  (0x30..0x39)   -> 0x30 + digit
 *   last byte, negative value         : 'p'..'y'  (0x70..0x79)   -> 0x70 + digit
 * </pre>
 *
 * The decimal point is implied (V) and never stored. Values are exact
 * {@link BigDecimal}s with a fixed {@code scale} = number of V digits; never
 * {@code double}/{@code float} (regulated-finance precision requirement).
 *
 * <p>NOTE: the sample data in {@code app/data/ASCII} ships signed fields in
 * EBCDIC zone-overpunch ('{','A'..'I','}','J'..'R'); GnuCOBOL on ASCII misreads
 * those during arithmetic, so datasets are transcoded to the native convention
 * above by {@code normalize_dataset.py}. This codec is the Java counterpart and
 * matches the GnuCOBOL oracle byte-for-byte.
 */
public final class ZonedDecimal {

    private ZonedDecimal() {}

    /** Decode a signed zoned-decimal field at {@code buf[off..off+len)} with the given decimal {@code scale}. */
    public static BigDecimal decode(byte[] buf, int off, int len, int scale) {
        StringBuilder digits = new StringBuilder(len);
        boolean negative = false;
        for (int i = 0; i < len; i++) {
            int b = buf[off + i] & 0xFF;
            int digit;
            if (i == len - 1) {
                if (b >= 0x30 && b <= 0x39) {
                    digit = b - 0x30;
                } else if (b >= 0x70 && b <= 0x79) {
                    digit = b - 0x70;
                    negative = true;
                } else {
                    throw new IllegalArgumentException(
                            "invalid zoned-decimal sign byte 0x" + Integer.toHexString(b) + " at offset " + (off + i));
                }
            } else {
                if (b < 0x30 || b > 0x39) {
                    throw new IllegalArgumentException(
                            "invalid zoned-decimal digit byte 0x" + Integer.toHexString(b) + " at offset " + (off + i));
                }
                digit = b - 0x30;
            }
            digits.append((char) ('0' + digit));
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        if (negative) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, scale);
    }

    /** Encode {@code value} into a fresh {@code len}-byte field with the given decimal {@code scale}. */
    public static byte[] encode(BigDecimal value, int len, int scale) {
        byte[] out = new byte[len];
        encodeInto(out, 0, value, len, scale);
        return out;
    }

    /** Encode {@code value} into {@code buf[off..off+len)} with the given decimal {@code scale}. */
    public static void encodeInto(byte[] buf, int off, BigDecimal value, int len, int scale) {
        // setScale without a rounding mode throws if the value carries more precision than the
        // field allows — a guard that a scale mismatch never silently corrupts a money amount.
        BigDecimal v = value.setScale(scale);
        boolean negative = v.signum() < 0;
        String digits = v.unscaledValue().abs().toString();
        if (digits.length() > len) {
            // COBOL stores the low-order digits and silently drops high-order overflow.
            digits = digits.substring(digits.length() - len);
        } else if (digits.length() < len) {
            digits = "0".repeat(len - digits.length()) + digits;
        }
        for (int i = 0; i < len; i++) {
            int digit = digits.charAt(i) - '0';
            int b = (i == len - 1 && negative) ? 0x70 + digit : 0x30 + digit;
            buf[off + i] = (byte) b;
        }
    }

    /**
     * Reduce {@code value} to what a PIC S9(intDigits)V9(decDigits) field can hold, mirroring COBOL's
     * silent high-order truncation when a result overflows the receiving field (no ON SIZE ERROR).
     * For values that fit, this is the identity.
     */
    public static BigDecimal truncateToField(BigDecimal value, int intDigits, int decDigits) {
        BigDecimal v = value.setScale(decDigits);
        BigInteger modulus = BigInteger.TEN.pow(intDigits + decDigits);
        BigInteger unscaled = v.unscaledValue().abs().mod(modulus);
        if (v.signum() < 0) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, decDigits);
    }
}
