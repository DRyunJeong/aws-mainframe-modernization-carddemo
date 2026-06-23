package com.carddemo.batch.io;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the native-ASCII zoned-decimal codec. The expected byte
 * patterns are exactly what GnuCOBOL 3.2 emits (pinned with src/test/cobol/SIGNPROBE.cbl):
 * positive last byte '0'..'9' (0x30+d), negative last byte 'p'..'y' (0x70+d).
 */
class ZonedDecimalTest {

    private static byte[] bytes(String s) {
        return s.getBytes(Cobol.CHARSET);
    }

    @Test
    void encodesPositiveWithPlainTrailingDigit() {
        assertArrayEquals(bytes("00000000000"), ZonedDecimal.encode(new BigDecimal("0.00"), 11, 2));
        assertArrayEquals(bytes("000000019400"), ZonedDecimal.encode(new BigDecimal("194.00"), 12, 2));
        assertArrayEquals(bytes("00000012345"), ZonedDecimal.encode(new BigDecimal("123.45"), 11, 2));
    }

    @Test
    void encodesNegativeWithOverpunchTrailingByte() {
        // -194.00 -> last digit 0, negative -> 0x70 = 'p'
        assertArrayEquals(bytes("00000001940p"), ZonedDecimal.encode(new BigDecimal("-194.00"), 12, 2));
        // -0.05 -> last digit 5, negative -> 0x75 = 'u'
        assertArrayEquals(bytes("0000000000u"), ZonedDecimal.encode(new BigDecimal("-0.05"), 11, 2));
        // -123.45 -> last digit 5, negative -> 'u'
        assertArrayEquals(bytes("0000001234u"), ZonedDecimal.encode(new BigDecimal("-123.45"), 11, 2));
    }

    @Test
    void decodeIsInverseOfEncode() {
        for (String v : new String[]{"0.00", "194.00", "-194.00", "0.01", "-0.01",
                "9999999.99", "-9999999.99", "123.45", "-0.05"}) {
            BigDecimal value = new BigDecimal(v);
            byte[] enc = ZonedDecimal.encode(value, 12, 2);
            assertEquals(value, ZonedDecimal.decode(enc, 0, 12, 2), "round-trip " + v);
        }
    }

    @Test
    void decodesNativeBytesToSignedValue() {
        assertEquals(new BigDecimal("194.00"), ZonedDecimal.decode(bytes("000000019400"), 0, 12, 2));
        assertEquals(new BigDecimal("-194.00"), ZonedDecimal.decode(bytes("00000001940p"), 0, 12, 2));
        assertEquals(new BigDecimal("-0.05"), ZonedDecimal.decode(bytes("0000000000u"), 0, 11, 2));
    }

    @Test
    void truncateToFieldDropsHighOrderDigitsOnOverflow() {
        // S9(09)V99 holds 9 integer digits; 8,333,324,999.91 overflows -> drop high-order
        BigDecimal big = new BigDecimal("8333324999.91");
        assertEquals(new BigDecimal("333324999.91"), ZonedDecimal.truncateToField(big, 9, 2));
        // values that fit are unchanged
        assertEquals(new BigDecimal("194.00"), ZonedDecimal.truncateToField(new BigDecimal("194.00"), 9, 2));
        assertEquals(new BigDecimal("-3.61"), ZonedDecimal.truncateToField(new BigDecimal("-3.61"), 9, 2));
    }
}
