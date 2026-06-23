package com.carddemo.batch.io;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the packed-decimal (COMP-3) codec. The expected byte patterns are
 * exactly what GnuCOBOL emits for PIC S9(10)V99 COMP-3 (pinned via CBACT01C's
 * OUTFILE/ARRYFILE golden): 7 bytes, sign nibble 0xC positive / 0xD negative.
 */
class Comp3Test {

    private static byte[] bytes(int... ints) {
        byte[] b = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            b[i] = (byte) ints[i];
        }
        return b;
    }

    @Test
    void encodesPositiveAndNegativePackedDecimal() {
        // 2525.00 -> 00 00 00 02 52 50 0C ; -2500.00 -> 00 00 00 02 50 00 0D
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x02, 0x52, 0x50, 0x0C),
                Comp3.encode(new BigDecimal("2525.00"), 12, 2));
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x02, 0x50, 0x00, 0x0D),
                Comp3.encode(new BigDecimal("-2500.00"), 12, 2));
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C),
                Comp3.encode(BigDecimal.ZERO.setScale(2), 12, 2));
    }

    @Test
    void decodeIsInverseOfEncode() {
        for (String v : new String[]{"0.00", "2525.00", "-2500.00", "1234.56", "-99.99", "9999999999.99"}) {
            BigDecimal value = new BigDecimal(v);
            byte[] enc = Comp3.encode(value, 12, 2);
            assertEquals(7, enc.length, "S9(10)V99 COMP-3 is 7 bytes");
            assertEquals(value, Comp3.decode(enc, 0, 12, 2), "round-trip " + v);
        }
    }
}
