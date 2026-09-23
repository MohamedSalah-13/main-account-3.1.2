package com.hamza.account.features.treasury.statement;

import com.hamza.account.features.currency.Currency;

import java.math.BigDecimal;

/**
 * Which of a statement's two sets of figures it shows (docs/currency-plan.md §13).
 * <p>
 * <b>A statement of one treasury in a foreign currency is written in that currency.</b> A dollar drawer
 * is counted in dollars, and its statement is what the count is checked against: opened with 100,
 * given 50, 20 taken out, 100 bought and 30 sold is 200 dollars - the figure the treasuries screen and
 * every withdrawal already read as its balance ({@code balance_own}). Its book value in the base is a
 * different question, with an answer of its own beside it.
 * <p>
 * <b>Every other statement is in the base</b>: one treasury in the base, and all treasuries at once -
 * a total of several treasuries in their own currencies would add dollars to pounds, and the base is
 * the one currency they share.
 *
 * @param foreign the treasury's currency, or {@code null} for the base
 */
public record TreasuryStatementCurrency(Currency foreign) {

    public static final TreasuryStatementCurrency BASE = new TreasuryStatementCurrency(null);

    /** True when the figures shown are a foreign treasury's own, not the books'. */
    public boolean isForeign() {
        return foreign != null;
    }

    public BigDecimal income(TreasuryStatementRow row) {
        return isForeign() ? row.incomeOwn() : row.income();
    }

    public BigDecimal output(TreasuryStatementRow row) {
        return isForeign() ? row.outputOwn() : row.output();
    }

    public BigDecimal runningBalance(TreasuryStatementRow row) {
        return isForeign() ? row.runningBalanceOwn() : row.runningBalance();
    }

    public TreasuryStatementSummary summary(TreasuryStatementTotals totals) {
        return isForeign() ? totals.own() : totals.base();
    }

    /** The code written beside the figures - "USD" - or empty for the base, which writes none. */
    public String code() {
        return isForeign() ? foreign.code() : "";
    }
}
