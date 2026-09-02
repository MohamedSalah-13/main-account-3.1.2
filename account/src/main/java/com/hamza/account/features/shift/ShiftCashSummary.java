package com.hamza.account.features.shift;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.treasury.MovementLabel;

import java.util.List;
import java.math.BigDecimal;

/**
 * Turns a shift's cash movements into the summary a cashier is judged by.
 * <p>
 * The expected balance is the opening cash plus everything that entered the till
 * minus everything that left it - <b>every</b> heading, not the five the screen
 * happens to show. It used to be assembled from four hand-written queries
 * (sales, sales returns, expenses, deposits/withdrawals) and that was the defect:
 * a cashier who collected 5,000 from a customer's account finished the day 5,000
 * over, and one who paid a supplier in cash finished short by what they paid, for
 * doing their job correctly. Ten headings reach a till; four were counted.
 * <p>
 * So the named totals here are a <em>breakdown for the report</em> and nothing is
 * derived from them. {@link ShiftSummary#getExpectedBalance()} reads
 * {@code totalIn}/{@code totalOut}, which are sums over the whole list, and
 * {@code otherIn}/{@code otherOut} carry whatever the named five do not - so
 * every pound is on the screen somewhere even when a heading has no line of its
 * own.
 * <p>
 * The opening balance heading is not a movement of this shift - it is what the
 * treasury held before the system knew about it, and it belongs to the till, not
 * to the day. It is dropped here for the same reason
 * {@code treasury_current_balance} excludes it from its sums.
 * <p>
 * No JavaFX and no database: the caller hands over rows.
 */
public final class ShiftCashSummary {

    private ShiftCashSummary() {
    }

    public static ShiftSummary summarize(List<ShiftCashMovement> movements, BigDecimal openBalance, int invoicesCount) {
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal salesReturns = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal deposits = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;

        for (ShiftCashMovement movement : movements) {
            if (movement == null || movement.label() == MovementLabel.OPENING) continue;

            totalIn = totalIn.add(movement.income());
            totalOut = totalOut.add(movement.output());

            switch (movement.label()) {
                case SALES -> sales = sales.add(movement.income());
                case SALES_RETURNS -> salesReturns = salesReturns.add(movement.output());
                case EXPENSES -> expenses = expenses.add(movement.output());
                case DEPOSIT -> deposits = deposits.add(movement.income());
                case WITHDRAWAL -> withdrawals = withdrawals.add(movement.output());
                default -> {
                    // Purchases, purchase returns, customer collections, supplier
                    // payments and both sides of a transfer. They have no column of
                    // their own; otherIn/otherOut below are what keeps them counted.
                }
            }
        }

        return ShiftSummary.builder()
                .openBalance(openBalance)
                .totalSales(sales)
                .totalSalesReturns(salesReturns)
                .totalExpenses(expenses)
                .totalDeposits(deposits)
                .totalWithdrawals(withdrawals)
                .otherIn(totalIn.subtract(sales).subtract(deposits))
                .otherOut(totalOut.subtract(salesReturns).subtract(expenses).subtract(withdrawals))
                .totalIn(totalIn)
                .totalOut(totalOut)
                .invoicesCount(invoicesCount)
                .build();
    }
}
