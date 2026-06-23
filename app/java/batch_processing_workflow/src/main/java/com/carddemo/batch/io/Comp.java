package com.carddemo.batch.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Codec for COBOL binary numerics ({@code USAGE COMP}/{@code COMPUTATIONAL}/
 * {@code BINARY}). Pinned to what GnuCOBOL emits under {@code --std=ibm} on this
 * platform (verified with a probe program):
 * <ul>
 *   <li><b>big-endian</b> byte order (e.g. {@code 9(9) COMP} = 305419896 &rarr;
 *       {@code 12 34 56 78});</li>
 *   <li><b>two's complement</b> for signed fields (e.g. {@code S9(10)V99 COMP} =
 *       -12345.67 &rarr; {@code FF FF FF FF FF ED 29 79});</li>
 *   <li>IBM byte width by digit count: 1–4 digits &rarr; 2 bytes, 5–9 &rarr; 4,
 *       10–18 &rarr; 8.</li>
 * </ul>
 * Values are exact {@link BigDecimal}s with a fixed decimal {@code scale}; never
 * {@code double}/{@code float}. On store, GnuCOBOL truncates to the declared digit
 * count ({@code binary-truncate}); this codec mirrors that with a modulo reduction.
 */
public final class Comp {

    private Comp() {}

    /** IBM binary field width (bytes) for a PIC with {@code totalDigits} digits. */
    public static int byteWidth(int totalDigits) {
        if (totalDigits <= 4) {
            return 2;
        }
        if (totalDigits <= 9) {
            return 4;
        }
        return 8;   // 10..18 digits
    }

    /** Encode {@code value} into {@code buf[off..]} as a big-endian binary field. */
    public static void encodeInto(byte[] buf, int off, BigDecimal value,
                                  int totalDigits, int scale, boolean signed) {
        int width = byteWidth(totalDigits);
        BigInteger unscaled = value.setScale(scale, RoundingMode.DOWN).unscaledValue();
        BigInteger mod = BigInteger.TEN.pow(totalDigits);               // binary-truncate to digit count
        BigInteger mag = unscaled.abs().mod(mod);
        BigInteger stored = (signed && unscaled.signum() < 0) ? mag.negate() : mag;

        byte[] full = stored.toByteArray();                             // big-endian two's complement, minimal
        byte fill = (byte) (stored.signum() < 0 ? 0xFF : 0x00);
        byte[] out = new byte[width];
        Arrays.fill(out, fill);
        int copy = Math.min(full.length, width);                        // keep the low-order bytes
        System.arraycopy(full, full.length - copy, out, width - copy, copy);
        System.arraycopy(out, 0, buf, off, width);
    }

    /** Decode a big-endian binary field at {@code buf[off..off+byteWidth(totalDigits))}. */
    public static BigDecimal decode(byte[] buf, int off, int totalDigits, int scale, boolean signed) {
        int width = byteWidth(totalDigits);
        byte[] slice = Arrays.copyOfRange(buf, off, off + width);
        BigInteger v = signed ? new BigInteger(slice)                   // two's complement
                              : new BigInteger(1, slice);               // unsigned magnitude
        return new BigDecimal(v, scale);
    }
}
