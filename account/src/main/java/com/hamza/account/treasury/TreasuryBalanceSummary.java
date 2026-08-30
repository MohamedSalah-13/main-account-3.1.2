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
                                     BigDecimal balance) {

    public boolean isEmpty() {
        return balance.signum() == 0;
    }

    public boolean isNegative() {
        return balance.signum() < 0;
    }
}
