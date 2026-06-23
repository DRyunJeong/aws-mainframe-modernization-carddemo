package com.carddemo.batch.io;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Codec for COBOL packed-decimal ({@code USAGE COMP-3}) numerics. Two digits per
 * byte; the low nibble of the last byte is the sign — 0xC positive / 0xD negative
 * for a signed PIC ({@code S9...}), or 0xF for an unsigned PIC ({@code 9...},
 * GnuCOBOL's convention). A field of {@code totalDigits} digits occupies
 * {@code (totalDigits / 2) + 1} bytes, with an unused leading nibble when the
 * digit count is even. Money values are exact {@link BigDecimal}s with a fixed
 * decimal {@code scale}; never {@code double}/{@code float}.
 */
public final class Comp3 {

    private Comp3() {}

    /** Number of packed bytes for a PIC with {@code totalDigits} digits. */
    public static int byteLength(int totalDigits) {
        return totalDigits / 2 + 1;
    }

    /** Encode {@code value} (signed PIC) into a fresh packed field. */
    public static byte[] encode(BigDecimal value, int totalDigits, int scale) {
        byte[] out = new byte[byteLength(totalDigits)];
        encodeInto(out, 0, value, totalDigits, scale);
        return out;
    }

    /** Encode {@code value} into {@code buf[off..]} as a <b>signed</b> packed field (0xC/0xD sign). */
    public static void encodeInto(byte[] buf, int off, BigDecimal value, int totalDigits, int scale) {
        encodeInto(buf, off, value, totalDigits, scale, true);
    }

    /**
     * Encode {@code value} into {@code buf[off..]} as a packed field of {@code totalDigits}
     * digits. {@code signed} selects the sign nibble: 0xC/0xD for a signed PIC, 0xF for an
     * unsigned PIC (e.g. {@code 9(03) COMP-3}).
     */
    public static void encodeInto(byte[] buf, int off, BigDecimal value, int totalDigits, int scale, boolean signed) {
        BigDecimal v = value.setScale(scale);
        String digits = String.format("%0" + totalDigits + "d",
                v.abs().unscaledValue().mod(BigInteger.TEN.pow(totalDigits)));   // low-order on overflow
        int len = byteLength(totalDigits);
        int[] nib = new int[len * 2];                       // leading nibble(s) default 0
        nib[len * 2 - 1] = !signed ? 0x0F : (v.signum() < 0 ? 0x0D : 0x0C);   // sign in the last low nibble
        for (int i = 0; i < totalDigits; i++) {
            nib[len * 2 - 2 - i] = digits.charAt(totalDigits - 1 - i) - '0';
        }
        for (int b = 0; b < len; b++) {
            buf[off + b] = (byte) ((nib[2 * b] << 4) | nib[2 * b + 1]);
        }
    }

    /** Decode a packed field at {@code buf[off..off+byteLength(totalDigits))}. */
    public static BigDecimal decode(byte[] buf, int off, int totalDigits, int scale) {
        int len = byteLength(totalDigits);
        StringBuilder digits = new StringBuilder(totalDigits);
        int totalNibbles = len * 2;
        int signNibble = buf[off + len - 1] & 0x0F;
        for (int n = totalNibbles - 1 - totalDigits; n < totalNibbles - 1; n++) {
            int nibble = (n % 2 == 0) ? ((buf[off + n / 2] >> 4) & 0x0F) : (buf[off + n / 2] & 0x0F);
            digits.append((char) ('0' + nibble));
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        if (signNibble == 0x0D || signNibble == 0x0B) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, scale);
    }
}
