package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossRow;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * One month of the report: its profit and loss, the same month a year earlier, what its sales were made of,
 * and the days behind it.
 *
 * @param days the statement's own days in this month, oldest first - what the row opens into
 */
public record YearlyReportRow(YearMonth month, ProfitLossFigures current, ProfitLossFigures previous,
                              MonthBreakdown breakdown, List<ProfitLossRow> days) {

    public YearlyReportRow {
        if (breakdown.month() != month.getMonthValue()) {
            throw new IllegalArgumentException("The breakdown of month " + breakdown.month()
                    + " was given to " + month);
        }
        days = List.copyOf(days);
    }

    public BigDecimal netSales() {
        return current.netSales();
    }

    public BigDecimal costOfSales() {
        return current.costOfSales();
    }

    public BigDecimal grossProfit() {
        return current.grossProfit();
    }

    public BigDecimal expenses() {
        return current.expenses();
    }

    public BigDecimal netProfit() {
        return current.netProfit();
    }

    public Optional<BigDecimal> grossMargin() {
        return current.grossMargin();
    }

    public Optional<BigDecimal> netMargin() {
        return current.netMargin();
    }

    public BigDecimal grossSales() {
        return breakdown.grossSales();
    }

    public BigDecimal salesDiscount() {
        return breakdown.salesDiscount();
    }

    public BigDecimal salesReturns() {
        return breakdown.salesReturns();
    }

    public BigDecimal netPurchases() {
        return breakdown.netPurchases();
    }

    public BigDecimal previousNetSales() {
        return previous.netSales();
    }

    public BigDecimal previousNetProfit() {
        return previous.netProfit();
    }

    public Optional<BigDecimal> netSalesChange() {
        return ProfitLossFigures.change(current.netSales(), previous.netSales());
    }

    public Optional<BigDecimal> netProfitChange() {
        return ProfitLossFigures.change(current.netProfit(), previous.netProfit());
    }

    public boolean hasActivity() {
        return current.hasActivity();
    }

    /**
     * The statement's net sales less what the breakdown says they are made of. Both read {@code total -
     * discount} over the same documents on the same dates, so it is zero - and if it ever is not, the two
     * definitions have drifted apart and the columns on the screen no longer add up.
     */
    public BigDecimal unexplainedSales() {
        return current.netSales().subtract(breakdown.netSales());
    }
}
