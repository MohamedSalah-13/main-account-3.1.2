package com.hamza.account.features.report.summary;

import com.hamza.account.features.report.monthly.MonthFigures;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The summary's paper: a line per figure the reader may see, this period's beside the period before's.
 * What is as at today - the customers' debts - has no figure for the period before, and says so with a
 * blank rather than a zero.
 */
public final class SummaryPaper {

    /**
     * @param captionKey the figure's name, a message key
     * @param previous   the period before's, or {@code null} for a figure that is as at today
     * @param count      whether it is a count, written as a whole number, rather than an amount
     */
    public record Line(String captionKey, BigDecimal current, BigDecimal previous, boolean count) {
    }

    private SummaryPaper() {
    }

    public static List<Line> lines(Summary summary) {
        List<Line> lines = new ArrayList<>();
        if (summary.sales() != null) {
            figures(lines, "report.dashboard.paper.sales", summary.sales(), summary.previousSales());
        }
        if (summary.purchases() != null) {
            figures(lines, "report.dashboard.paper.purchases", summary.purchases(), summary.previousPurchases());
        }
        if (summary.cash() != null) {
            lines.add(new Line("report.dashboard.paper.cash.in", summary.cash().in(), summary.previousCash().in(),
                    false));
            lines.add(new Line("report.dashboard.paper.cash.out", summary.cash().out(),
                    summary.previousCash().out(), false));
            lines.add(new Line("report.dashboard.paper.cash.net", summary.cash().net(),
                    summary.previousCash().net(), false));
        }
        if (summary.receivables() != null) {
            lines.add(new Line("report.dashboard.paper.receivables", summary.receivables().owed(), null, false));
            lines.add(new Line("report.dashboard.paper.debtors",
                    BigDecimal.valueOf(summary.receivables().debtors()), null, true));
        }
        return lines;
    }

    /** A side's net, its invoices, its discounts and what came back - the pieces the net is made of. */
    private static void figures(List<Line> lines, String prefix, MonthFigures current, MonthFigures previous) {
        add(lines, prefix + ".net", current, previous, MonthFigures::net, false);
        add(lines, prefix + ".invoices", current, previous, figures -> BigDecimal.valueOf(figures.invoices()), true);
        add(lines, prefix + ".discount", current, previous, MonthFigures::discount, false);
        add(lines, prefix + ".returns", current, previous, MonthFigures::returns, false);
    }

    private static void add(List<Line> lines, String key, MonthFigures current, MonthFigures previous,
                            Function<MonthFigures, BigDecimal> figure, boolean count) {
        lines.add(new Line(key, figure.apply(current), figure.apply(previous), count));
    }
}
