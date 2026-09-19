package com.hamza.account.features.delegate;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * The most a sales invoice carrying a delegate may be discounted: a percentage of the invoice
 * before any discount. No database and no session - the whole rule, over plain values.
 * <p>
 * Three decisions, each with a test:
 * <ul>
 *   <li><b>No ceiling is not a ceiling of zero.</b> An absent one allows everything, which is
 *       every employee on the day {@code V73} arrives; zero is a delegate who may discount
 *       nothing. The same line a filter field and a commission tier draw.</li>
 *   <li><b>What is judged is every discount on the document</b> - the lines' and the header's
 *       together - against the lines' total before discount. A ceiling on the header alone is
 *       walked round through the discount column of a line.</li>
 *   <li><b>Amounts are compared, never the rounded percentage.</b> 10.004% is shown as 10.00% and
 *       is not inside a ceiling of 10% - the {@link CommissionTiers} lesson. The allowed amount is
 *       rounded as money, half up, so a ceiling that works out to 10.005 allows 10.01.</li>
 * </ul>
 */
public record DiscountCeiling(BigDecimal maxPercent) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public DiscountCeiling {
        if (maxPercent == null || maxPercent.signum() < 0 || maxPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("a discount ceiling is a percentage from 0 to 100");
        }
    }

    /** A stored value as a ceiling; {@code null} in the column is no ceiling at all. */
    public static Optional<DiscountCeiling> ofStored(BigDecimal stored) {
        return stored == null ? Optional.empty() : Optional.of(new DiscountCeiling(stored));
    }

    /** The largest discount, in money, a document of this gross total may carry. */
    public BigDecimal allowedOn(BigDecimal gross) {
        if (gross == null || gross.signum() <= 0) {
            return MoneyMath.money(BigDecimal.ZERO);
        }
        return gross.multiply(maxPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * Whether a document's discounts, taken together, are more than this ceiling allows.
     *
     * @param gross          the lines before any discount
     * @param lineDiscount   what the lines themselves took off
     * @param headerDiscount what the invoice took off on top
     */
    public boolean exceededBy(BigDecimal gross, BigDecimal lineDiscount, BigDecimal headerDiscount) {
        BigDecimal discount = MoneyMath.money(orZero(lineDiscount).add(orZero(headerDiscount)));
        return discount.compareTo(allowedOn(gross)) > 0;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
