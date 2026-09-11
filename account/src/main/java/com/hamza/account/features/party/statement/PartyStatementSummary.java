package com.hamza.account.features.party.statement;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;

/**
 * The four figures at the foot of a statement.
 * <p>
 * <b>{@code openingBalance} and {@code closingBalance} are real balances; the two period
 * totals answer the filter.</b> The same split {@code TreasuryStatements.SELECT_STATEMENT_SUMMARY}
 * makes, for the same reason: a balance is the sum of everything up to a date, so
 * narrowing it by movement kind or by till produces a number that is not anybody's
 * balance. {@link PartyStatementFilter#narrowsRows()} is what the screen uses to say that
 * the totals describe a narrowed list.
 * <p>
 * <b>This is the figure the old screen did not have at all.</b>
 * {@code AccountDetailsWithItemsController.filterByDate} filtered an in-memory list and
 * then recomputed the running balance from zero, so a statement for September was printed
 * as though the customer had started September owing nothing. Every reported balance on
 * such a page was short by the whole of the customer's history.
 *
 * @param openingBalance what the party owed before {@code from} — every movement, whatever
 *                       the other filters say
 * @param totalDebit     the debtor column summed over the rows actually shown
 * @param totalCredit    the creditor column summed over the rows actually shown
 * @param closingBalance what they owed at the end of {@code to} — again every movement
 */
public record PartyStatementSummary(
        BigDecimal openingBalance,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        BigDecimal closingBalance) {

    public static final PartyStatementSummary EMPTY = new PartyStatementSummary(null, null, null, null);

    public PartyStatementSummary {
        openingBalance = MoneyMath.money(openingBalance == null ? BigDecimal.ZERO : openingBalance);
        totalDebit = MoneyMath.money(totalDebit == null ? BigDecimal.ZERO : totalDebit);
        totalCredit = MoneyMath.money(totalCredit == null ? BigDecimal.ZERO : totalCredit);
        closingBalance = MoneyMath.money(closingBalance == null ? BigDecimal.ZERO : closingBalance);
    }

    /** What the shown rows came to. Equals {@code closing - opening} only when nothing is filtered out. */
    public BigDecimal netMovement() {
        return MoneyMath.subtract(totalDebit, totalCredit);
    }

    /**
     * Whether the shown rows account for the whole move from opening to closing.
     * <p>
     * False means a filter is hiding movements, and the screen says so rather than
     * letting a reader subtract two numbers that do not meet.
     */
    public boolean rowsExplainTheBalance() {
        return MoneyMath.add(openingBalance, netMovement()).compareTo(closingBalance) == 0;
    }
}
