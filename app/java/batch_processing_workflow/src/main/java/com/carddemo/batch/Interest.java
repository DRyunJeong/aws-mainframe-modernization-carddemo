package com.carddemo.batch;

import com.carddemo.batch.io.ZonedDecimal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The core monthly-interest arithmetic of {@code 1300-COMPUTE-INTEREST}:
 *
 * <pre>
 *   COMPUTE WS-MONTHLY-INT = ( TRAN-CAT-BAL * DIS-INT-RATE) / 1200
 * </pre>
 *
 * Both operands are exact decimals with scale 2; their product is exact (scale 4).
 * The COMPUTE has <b>no ROUNDED</b> clause, so COBOL <b>truncates</b> the result to
 * the receiver's scale (2) — reproduced with {@link RoundingMode#DOWN} (truncate
 * toward zero, matching COBOL for negative results too). Using HALF_UP here would
 * diverge from the oracle on values with a non-terminating quotient.
 *
 * <p>{@code WS-MONTHLY-INT} is PIC S9(09)V99, so the result is reduced to that
 * field's capacity (mirrors COBOL silent high-order truncation on overflow).
 */
public final class Interest {

    static final BigDecimal DIVISOR_1200 = BigDecimal.valueOf(1200);
    static final int MONTHLY_SCALE = 2;
    static final int MONTHLY_INT_DIGITS = 9;   // S9(09)V99

    private Interest() {}

    public static BigDecimal monthly(BigDecimal balance, BigDecimal annualRatePercent) {
        BigDecimal product = balance.multiply(annualRatePercent);                  // exact, scale 4
        BigDecimal monthly = product.divide(DIVISOR_1200, MONTHLY_SCALE, RoundingMode.DOWN);
        return ZonedDecimal.truncateToField(monthly, MONTHLY_INT_DIGITS, MONTHLY_SCALE);
    }
}
