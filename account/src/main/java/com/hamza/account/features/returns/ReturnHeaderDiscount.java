package com.hamza.account.features.returns;

import com.hamza.account.finance.MoneyMath;

/**
 * The share of a source invoice's own discount that a return of part of it gives back.
 * <p>
 * A document carries two kinds of discount. A line's belongs to that line, and a picked
 * return line already takes its proportional share ({@code ReturnableLineSelection
 * .discountShareFor}). The other is taken on the document as a whole - the "additional
 * discount" box - and until this class nothing carried it across at all: a sale of 1000
 * with 100 off was paid 900, and returning all of it from the picker refunded 1000,
 * because the lines came back at their own prices and the return's discount box stayed
 * at zero. The 100 left the till, or was credited to the customer's account, out of
 * nothing, and the return showed a bigger loss than the sale had shown profit.
 * <p>
 * The share is by value, not by quantity: the document discount has no owner among the
 * lines, and the one allocation that needs no invented rule is the fraction of the
 * source's {@code total} the return's own {@code total} is. Both totals are the lines
 * after their own discounts, which is what the header column holds on all four families.
 * <p>
 * One definition with two readers, on purpose: the screen fills the box from
 * {@link #shareFor} and {@code ReturnGuard} refuses a save that disagrees with it, the
 * way {@code discountShareFor} and {@code ReturnCostResolver} already are for a line.
 */
public final class ReturnHeaderDiscount {

    /**
     * One piastre. Wider than the half-piastre a line is held to, because this share is a
     * product of three rounded figures and several partial returns of one invoice must
     * each be able to land on their own rounded share.
     */
    public static final double TOLERANCE = 0.01;

    private ReturnHeaderDiscount() {
    }

    /**
     * @param sourceTotal    the source header's {@code total}
     * @param sourceDiscount the source header's {@code discount}
     * @param returnTotal    the return's own {@code total}
     * @return what the return's discount has to be; zero when the source took none
     */
    public static double shareFor(double sourceTotal, double sourceDiscount, double returnTotal) {
        if (sourceDiscount <= 0 || sourceTotal <= 0 || returnTotal <= 0) {
            return 0;
        }
        double fraction = Math.min(returnTotal / sourceTotal, 1.0);
        return MoneyMath.asDouble(MoneyMath.multiply(sourceDiscount, fraction));
    }

    /** Whether {@code entered} is the share, to within {@link #TOLERANCE}. */
    public static boolean matches(double entered, double expected) {
        return Math.abs(entered - expected) <= TOLERANCE + 1e-9;
    }
}
