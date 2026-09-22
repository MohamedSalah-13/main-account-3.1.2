package com.hamza.account.features.capital;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * What the business holds less what it owes, set against the owner's equity as the statement closes
 * it on the same day - {@code docs/reports-plan.md} §13.
 *
 * <p><b>It is a reconciliation, not a balance sheet, and says so.</b> Without a general ledger nothing
 * makes the two sides equal by construction; they are two readings of the same books that ought to
 * agree. What is known to separate them is shown on lines of its own - the parties' movements that
 * moved no cash, and the treasury's ordinary deposits and withdrawals, neither of which the profit
 * reads - and the rest is {@link #unexplained()}, shown rather than hidden, the ageing report's
 * {@code unallocated} rule. The stock valued at today's buy price, the differences of posted counts,
 * and anything dated after today are what usually remain in it.</p>
 *
 * <p>Employees are left out on purpose: the profit counts a salary on the day it is paid, so an
 * entitlement not yet paid is on neither side, and adding the employees' balances would turn every
 * salary paid without a recorded entitlement into an asset.</p>
 *
 * @param equity the closing figure of the equity statement for {@code asOf}
 */
public record EquityReconciliation(LocalDate asOf, ReconciliationFigures figures, BigDecimal equity) {

    public EquityReconciliation {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(figures, "figures");
        Objects.requireNonNull(equity, "equity");
    }

    public BigDecimal assets() {
        return figures.treasuries().add(figures.customersOwe()).add(figures.suppliersInAdvance()).add(figures.stock());
    }

    public BigDecimal liabilities() {
        return figures.customersInCredit().add(figures.suppliersOwed());
    }

    public BigDecimal netAssets() {
        return assets().subtract(liabilities());
    }

    /** Net assets less equity: what the two readings disagree by. */
    public BigDecimal difference() {
        return netAssets().subtract(equity);
    }

    /**
     * A customer's note raises what is owed to the business and a supplier's raises what it owes,
     * and neither is in the profit - so the customers' add to the difference and the suppliers' take
     * from it.
     */
    public BigDecimal partyNonCash() {
        return figures.customersNonCash().subtract(figures.suppliersNonCash());
    }

    public BigDecimal explained() {
        return partyNonCash().add(figures.ordinaryCash());
    }

    public BigDecimal unexplained() {
        return difference().subtract(explained());
    }

    /**
     * The reconciliation in reading order, for the screen, the paper and the spreadsheet alike. A
     * detail is part of the total after it; the liabilities are shown as the amounts they are and
     * subtracted by the total that follows.
     */
    public List<EquityStatementLine> lines() {
        return List.of(
                detail("capital.reconcile.treasuries", figures.treasuries()),
                detail("capital.reconcile.customers.owe", figures.customersOwe()),
                detail("capital.reconcile.suppliers.advance", figures.suppliersInAdvance()),
                detail("capital.reconcile.stock", figures.stock()),
                total("capital.reconcile.assets", assets()),
                detail("capital.reconcile.customers.credit", figures.customersInCredit()),
                detail("capital.reconcile.suppliers.owed", figures.suppliersOwed()),
                total("capital.reconcile.liabilities", liabilities()),
                total("capital.reconcile.net.assets", netAssets()),
                total("capital.reconcile.equity", equity),
                total("capital.reconcile.difference", difference()),
                detail("capital.reconcile.party.non.cash", partyNonCash()),
                detail("capital.reconcile.ordinary.cash", figures.ordinaryCash()),
                total("capital.reconcile.unexplained", unexplained()));
    }

    private static EquityStatementLine detail(String key, BigDecimal amount) {
        return new EquityStatementLine(key, amount, EquityStatementLine.Kind.DETAIL);
    }

    private static EquityStatementLine total(String key, BigDecimal amount) {
        return new EquityStatementLine(key, amount, EquityStatementLine.Kind.TOTAL);
    }
}
