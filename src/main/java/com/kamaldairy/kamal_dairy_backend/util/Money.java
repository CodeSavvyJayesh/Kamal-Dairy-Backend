package com.kamaldairy.kamal_dairy_backend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All money in the wallet and subscription engine is held as whole paise in a
 * long. Never a double: 0.1 + 0.2 is not 0.3 in floating point, and a ledger
 * that drifts by a paisa per transaction is a ledger nobody can reconcile.
 *
 * Rupee values only exist at the edges - reading Product.price (a legacy
 * double column) and rendering JSON for the frontend.
 */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {}

    /** Legacy double rupee price -> exact paise, rounded half-up. */
    public static long toPaise(double rupees) {
        return BigDecimal.valueOf(rupees)
                .multiply(HUNDRED)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Paise -> rupees with exactly two decimals, for JSON responses. */
    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * unit x quantity, less a whole-number percentage discount, rounded to the
     * nearest paisa. Integer maths throughout.
     */
    public static long discounted(long unitPaise, int quantity, int discountPercent) {
        if (discountPercent < 0 || discountPercent > 100) {
            throw new IllegalArgumentException("Discount must be between 0 and 100");
        }

        return BigDecimal.valueOf(unitPaise)
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(HUNDRED, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** "₹1,250.00" style label for emails and error messages. */
    public static String label(long paise) {
        return "₹" + String.format("%,.2f", toRupees(paise));
    }
}
