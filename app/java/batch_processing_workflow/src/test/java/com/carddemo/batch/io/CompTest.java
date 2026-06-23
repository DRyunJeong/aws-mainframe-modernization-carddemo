package com.carddemo.batch.io;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the binary (COMP) codec. Expected bytes are exactly what GnuCOBOL
 * emits under {@code --std=ibm} (pinned with a probe program): big-endian, two's
 * complement, IBM width 2/4/8 by digit count.
 */
class CompTest {

    private static byte[] bytes(int... ints) {
        byte[] b = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            b[i] = (byte) ints[i];
        }
        return b;
    }

    private static byte[] enc(BigDecimal v, int digits, int scale, boolean signed) {
        byte[] b = new byte[Comp.byteWidth(digits)];
        Comp.encodeInto(b, 0, v, digits, scale, signed);
        return b;
    }

    @Test
    void widthsFollowIbmAllocation() {
        assertEquals(2, Comp.byteWidth(3));    // 1..4 digits
        assertEquals(4, Comp.byteWidth(9));    // 5..9 digits
        assertEquals(8, Comp.byteWidth(11));   // 10..18 digits
        assertEquals(8, Comp.byteWidth(12));
    }

    @Test
    void encodesBigEndianAndTwosComplement() {
        // 9(9) COMP 305419896 = 0x12345678
        assertArrayEquals(bytes(0x12, 0x34, 0x56, 0x78), enc(new BigDecimal("305419896"), 9, 0, false));
        // 9(11) COMP 1234567890 = 0x499602D2 in 8 bytes
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x00, 0x49, 0x96, 0x02, 0xd2),
                enc(new BigDecimal("1234567890"), 11, 0, false));
        // 9(3) COMP 123 = 0x007B
        assertArrayEquals(bytes(0x00, 0x7b), enc(new BigDecimal("123"), 3, 0, false));
        // S9(10)V99 COMP -12345.67 -> scaled -1234567 -> two's complement 8 bytes
        assertArrayEquals(bytes(0xff, 0xff, 0xff, 0xff, 0xff, 0xed, 0x29, 0x79),
                enc(new BigDecimal("-12345.67"), 12, 2, true));
    }

    @Test
    void decodeIsInverseOfEncode() {
        check(new BigDecimal("305419896"), 9, 0, false);
        check(new BigDecimal("99999999999"), 11, 0, false);
        check(BigDecimal.ZERO.setScale(2), 12, 2, true);
        check(new BigDecimal("-9999999999.99"), 12, 2, true);
        check(new BigDecimal("12345.67"), 12, 2, true);
        check(new BigDecimal("999"), 3, 0, false);
    }

    private static void check(BigDecimal v, int digits, int scale, boolean signed) {
        byte[] enc = enc(v, digits, scale, signed);
        assertEquals(v, Comp.decode(enc, 0, digits, scale, signed), "round-trip " + v);
    }
}
