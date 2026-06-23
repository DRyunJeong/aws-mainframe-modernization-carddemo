package com.carddemo.batch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for the monthly-interest arithmetic. Expected values match the
 * GnuCOBOL oracle (synthetic datasets); see the golden-master test for the
 * byte-for-byte cross-check.
 */
class InterestTest {

    @Test
    void exactAndTruncatingResults() {
        assertEquals(new BigDecimal("15.00"), Interest.monthly(new BigDecimal("1000.00"), new BigDecimal("18.00")));
        assertEquals(new BigDecimal("1.50"), Interest.monthly(new BigDecimal("100.00"), new BigDecimal("18.00")));
        // 333.33 * 13 / 1200 = 3.6110... -> truncate -> 3.61
        assertEquals(new BigDecimal("3.61"), Interest.monthly(new BigDecimal("333.33"), new BigDecimal("13.00")));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() {
        // -333.33 * 13 / 1200 = -3.6110... -> truncate toward zero -> -3.61 (not -3.62)
        assertEquals(new BigDecimal("-3.61"), Interest.monthly(new BigDecimal("-333.33"), new BigDecimal("13.00")));
        assertEquals(new BigDecimal("-15.00"), Interest.monthly(new BigDecimal("-1000.00"), new BigDecimal("18.00")));
        // -7.77 * 22 / 1200 = -0.14245 -> -0.14
        assertEquals(new BigDecimal("-0.14"), Interest.monthly(new BigDecimal("-7.77"), new BigDecimal("22.00")));
    }

    @Test
    void overflowDropsHighOrderDigits() {
        // 999,999,999.99 * 9999.99 / 1200 overflows S9(09)V99 -> oracle value 333,324,999.91
        assertEquals(new BigDecimal("333324999.91"),
                Interest.monthly(new BigDecimal("999999999.99"), new BigDecimal("9999.99")));
    }

    /**
     * Negative control: the COBOL COMPUTE truncates (RoundingMode.DOWN). For these
     * values HALF_UP would give a different cent — proving the rounding mode is a
     * real behavioural decision, and that DOWN is the one matching the oracle.
     */
    @Test
    void truncationDiffersFromHalfUp() {
        // 100.40 * 15 / 1200 = 1.2550 ; 100.50 * 18 / 1200 = 1.5075
        assertEquals(new BigDecimal("1.25"), Interest.monthly(new BigDecimal("100.40"), new BigDecimal("15.00")));
        assertEquals(new BigDecimal("1.50"), Interest.monthly(new BigDecimal("100.50"), new BigDecimal("18.00")));

        BigDecimal halfUp = new BigDecimal("100.40").multiply(new BigDecimal("15.00"))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("1.26"), halfUp);
        assertNotEquals(halfUp, Interest.monthly(new BigDecimal("100.40"), new BigDecimal("15.00")));
    }
}
