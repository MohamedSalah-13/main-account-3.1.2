package com.hamza.account.treasury;

import java.math.BigDecimal;

/**
 * One row of {@code treasury_current_balance} - the single answer to "how much is
 * in this treasury".
 * <p>
 * Three numbers used to answer that question and none of them completely:
 * {@code treasury.amount} (written once at insert and never updated),
 * {@code treasury_balance} (the documents, without the opening balance or the
 * transfers) and {@code treasury_balance_after_convert} (the opening balance and
 * the transfers, without the documents). The view replaces all three, and this
 * record is how the application reads it - the screens, the dashboard and the
 * low-balance notification all go through here.
 * <p>
 * {@code balance} is {@code opening + totalIn - totalOut}, computed by the view.
 * {@code totalIn}/{@code totalOut} deliberately <b>exclude</b> the opening balance
 * so a statement can show "brought forward" apart from "moved this period"; adding
 * the opening into the totals as well would double it.
 * <p>
 * A plain record with no JavaFX and no {@code DForColumnTable}, per
 * {@code docs/new-code-rules.md}.
 * <p>
 * <b>A treasury in a foreign currency has two figures</b> (V81, docs/currency-plan.md §11).
 * {@code balance} is its book value in the base - what the books moved in and out of it, which is
 * what every total over treasuries adds up. {@code balanceOwn} is what it holds in its own currency,
 * the figure a withdrawal is checked against and a person counts. For a treasury in the base the two
 * are the same number, and {@code currencyId} is {@code null}.
 */
public record TreasuryBalanceSummary(int id,
                                     String name,
                                     TreasuryType type,
                                     boolean active,
                                     int sortOrder,
                                     BigDecimal feePercent,
                                     BigDecimal opening,
                                     BigDecimal totalIn,
                                     BigDecimal totalOut,
                                     BigDecimal balance,
                                     Integer currencyId,
                                     BigDecimal openingOwn,
                                     BigDecimal balanceOwn) {

    public TreasuryBalanceSummary {
        openingOwn = openingOwn == null ? opening : openingOwn;
        balanceOwn = balanceOwn == null ? balance : balanceOwn;
    }

    /** A treasury in the base - every caller that predates V81 builds one. */
    public TreasuryBalanceSummary(int id, String name, TreasuryType type, boolean active, int sortOrder,
                                  BigDecimal feePercent, BigDecimal opening, BigDecimal totalIn,
                                  BigDecimal totalOut, BigDecimal balance) {
        this(id, name, type, active, sortOrder, feePercent, opening, totalIn, totalOut, balance,
                null, opening, balance);
    }

    /** In a currency other than the base (V81). */
    public boolean isForeign() {
        return currencyId != null;
    }

    public boolean isEmpty() {
        return balance.signum() == 0;
    }

    public boolean isNegative() {
        return balance.signum() < 0;
    }
}
