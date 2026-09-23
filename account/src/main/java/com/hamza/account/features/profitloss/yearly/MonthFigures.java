package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.ProfitLossRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * One month of the profit and loss statement: the five figures {@code ProfitLossDao} answers for a day,
 * summed over the month's days.
 *
 * <p>Nothing here computes a profit. The figures arrive as the statement's own rows and are added up, so
 * the yearly report and the profit and loss screen cannot report two numbers for one month - the defect
 * {@code view_yearly_monthly_report} carried until its profit was made {@code document_profit}'s.</p>
 *
 * <p>A percentage with nothing to divide by is <b>absent</b>, not zero: a margin on no sales, or a change
 * against a month that sold nothing, is not a number.</p>
 */
public record MonthFigures(BigDecimal netSales, BigDecimal costOfSales, BigDecimal grossProfit,
                           BigDecimal expenses, BigDecimal netProfit) {

    public static final MonthFigures ZERO = new MonthFigures(BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public MonthFigures {
        netSales = orZero(netSales);
        costOfSales = orZero(costOfSales);
        grossProfit = orZero(grossProfit);
        expenses = orZero(expenses);
        netProfit = orZero(netProfit);
    }

    /** These figures with one day of the statement added. */
    public MonthFigures plus(ProfitLossRow day) {
        return new MonthFigures(netSales.add(orZero(day.netSales())), costOfSales.add(orZero(day.costOfSales())),
                grossProfit.add(orZero(day.grossProfit())), expenses.add(orZero(day.expenses())),
                netProfit.add(orZero(day.netProfit())));
    }

    public MonthFigures plus(MonthFigures other) {
        return new MonthFigures(netSales.add(other.netSales), costOfSales.add(other.costOfSales),
                grossProfit.add(other.grossProfit), expenses.add(other.expenses), netProfit.add(other.netProfit));
    }

    /** Whether anything was sold, returned or spent - a month of zeros is still a month on the report. */
    public boolean hasActivity() {
        return netSales.signum() != 0 || costOfSales.signum() != 0 || expenses.signum() != 0;
    }

    /** The gross profit as a share of what the month sold, net of returns and discounts. */
    public Optional<BigDecimal> grossMargin() {
        return marginOf(grossProfit);
    }

    /** The net profit as a share of what the month sold. */
    public Optional<BigDecimal> netMargin() {
        return marginOf(netProfit);
    }

    /** What the expenses took out of each hundred sold. */
    public Optional<BigDecimal> expenseShare() {
        return marginOf(expenses);
    }

    private Optional<BigDecimal> marginOf(BigDecimal part) {
        if (netSales.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED).divide(netSales, 2, RoundingMode.HALF_UP));
    }

    /**
     * How {@code current} moved against {@code previous}, as a percentage of the previous figure's size.
     * Divided by the magnitude, so a loss of 100 becoming a profit of 50 reads as a rise (+150%) rather
     * than a fall - the same rule as the collections trend.
     */
    public static Optional<BigDecimal> change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(current.subtract(previous).multiply(HUNDRED)
                .divide(previous.abs(), 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
