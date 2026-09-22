package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.controlsfx.table.Columns;

import java.math.BigDecimal;

/**
 * The figures the cashier's shift screen shows, and what a blind close leaves out.
 * <p>
 * <b>A blind close means the cashier counts the drawer without being told what it should hold.</b>
 * The screen used to hide the expected balance and the difference and show everything that makes
 * them up - the cash taken in sales, the deposits, the returns, the expenses, the withdrawals and
 * both "other" figures - so the expected balance was one addition away, in front of the person it
 * was being kept from. The X report printed the same rows. Under a blind close every amount is
 * withheld here, and what remains is what the cashier knows anyway: how many invoices they rang up,
 * and the float they were given (the opening balance, which the screen's header carries).
 * <p>
 * No JavaFX: the screen asks for the strings and paints them, and {@code ShiftReportLayout} answers
 * the same question for the paper.
 *
 * @param tone how the difference reads - a colour on the screen, and absent under a blind close
 */
public record ShiftScreenSummary(String sales, String returns, String expenses, String otherIn, String otherOut,
                                 String expected, String difference, String invoices, Tone tone) {

    /** What a figure reads when there is nothing to show, or when a blind close withholds it. */
    public static final String ABSENT = "-";

    public enum Tone {
        /** The drawer holds less than it should. */
        SHORT,
        /** It holds more. */
        OVER,
        /** It agrees. */
        BALANCED,
        /** Nothing is said - no open shift, or a blind close. */
        NONE
    }

    /** Every figure absent: no shift is open. */
    public static ShiftScreenSummary none() {
        return new ShiftScreenSummary(ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, Tone.NONE);
    }

    public static ShiftScreenSummary of(ShiftSummary summary, BigDecimal countedCash, boolean blindClose) {
        if (summary == null) {
            return none();
        }
        String invoices = String.valueOf(summary.getInvoicesCount());
        if (blindClose) {
            return new ShiftScreenSummary(ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, ABSENT, invoices, Tone.NONE);
        }
        BigDecimal difference = summary.calculateDifference(countedCash);
        return new ShiftScreenSummary(
                Columns.money(summary.getTotalSales()),
                Columns.money(summary.getTotalSalesReturns()),
                Columns.money(summary.getTotalExpenses()),
                Columns.money(summary.getOtherIn()),
                Columns.money(summary.getOtherOut()),
                Columns.money(summary.getExpectedBalance()),
                Columns.money(difference),
                invoices,
                difference.signum() < 0 ? Tone.SHORT : difference.signum() > 0 ? Tone.OVER : Tone.BALANCED);
    }
}
