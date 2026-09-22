package com.hamza.account.features.capital;

import com.hamza.account.features.party.trend.TrendGranularity;
import com.hamza.account.features.profitloss.ProfitLossRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The owner's equity over a period: where it stood when the period opened, what the owner paid in
 * and drew, what the business earned, and where it stood at the close.
 *
 * <p><b>It defines no figure.</b> The capital is {@link CapitalStatements}'; the profit is the
 * profit and loss screen's own rows, read through {@code ProfitLossService} and never recomputed -
 * {@code docs/reports-plan.md} §3.2. What is done here is the arithmetic that joins them, with no
 * JavaFX, so each line is tested.</p>
 *
 * <p><b>The opening balances are equity brought forward and never a movement</b> (§3.1). A treasury's
 * opening balance may be capital or may be years of earlier profit sitting in a drawer, and nothing
 * recorded says which; so it stands on a line of its own with the parties' and the stock's, and
 * whoever recorded the first capital as a {@code CAPITAL_IN} movement sees it under "paid in" instead -
 * never under both.</p>
 *
 * <p>This is not a balance sheet and does not claim to balance against one: without a general
 * ledger nothing proves the assets equal this figure (§8 records that decision as still open).</p>
 *
 * @param days          the owner's movements in the period, a row per day and treasury
 * @param profitDays    the profit and loss screen's rows for the period, a row per day
 * @param profitBefore  the profit and loss's net profit for everything before the period
 */
public record EquityStatement(CapitalFilter filter, BroughtForward broughtForward, CapitalBefore capitalBefore,
                              BigDecimal profitBefore, List<CapitalDay> days, List<ProfitLossRow> profitDays) {

    /** The most periods one chart draws - the trend chart's ceiling. */
    public static final int MAX_PERIODS = 120;

    public EquityStatement {
        days = List.copyOf(days);
        profitDays = List.copyOf(profitDays);
    }

    /** Brought forward, plus everything the owner and the business did before the period. */
    public BigDecimal opening() {
        return broughtForward.total().add(capitalBefore.net()).add(profitBefore);
    }

    public BigDecimal paidIn() {
        return days.stream().map(CapitalDay::paidIn).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal drawn() {
        return days.stream().map(CapitalDay::drawn).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal profit() {
        return profitDays.stream().map(ProfitLossRow::netProfit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal closing() {
        return opening().add(paidIn()).subtract(drawn()).add(profit());
    }

    public int movements() {
        return days.stream().mapToInt(CapitalDay::movements).sum();
    }

    /** A row per treasury the owner moved money through, the largest net first. */
    public List<CapitalByTreasuryRow> byTreasury() {
        Map<Integer, CapitalByTreasuryRow> rows = new LinkedHashMap<>();
        for (CapitalDay day : days) {
            rows.merge(day.treasuryId(),
                    new CapitalByTreasuryRow(day.treasuryId(), day.treasuryName(), day.paidIn(), day.drawn(), day.movements()),
                    (was, one) -> new CapitalByTreasuryRow(was.treasuryId(), was.treasuryName(),
                            was.paidIn().add(one.paidIn()), was.drawn().add(one.drawn()),
                            was.movements() + one.movements()));
        }
        return rows.values().stream()
                .sorted(Comparator.comparing(CapitalByTreasuryRow::net).reversed()
                        .thenComparing(CapitalByTreasuryRow::treasuryName))
                .toList();
    }

    public boolean canGroupBy(TrendGranularity granularity) {
        return granularity.periods(filter.from(), filter.to()) <= MAX_PERIODS;
    }

    /**
     * Every period of the range with the equity at its close, the empty periods included - equity
     * stands still through a month with no movement, and the line has to show it standing.
     */
    public List<EquityPeriod> periods(TrendGranularity granularity) {
        if (!canGroupBy(granularity)) {
            throw new IllegalArgumentException("too many " + granularity + " periods between "
                    + filter.from() + " and " + filter.to());
        }
        Map<LocalDate, BigDecimal[]> sums = new TreeMap<>();
        for (LocalDate start = granularity.start(filter.from()); !start.isAfter(filter.to());
             start = granularity.next(start)) {
            sums.put(start, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (CapitalDay day : days) {
            BigDecimal[] sum = sums.get(granularity.start(day.day()));
            sum[0] = sum[0].add(day.paidIn());
            sum[1] = sum[1].add(day.drawn());
        }
        for (ProfitLossRow row : profitDays) {
            BigDecimal[] sum = sums.get(granularity.start(row.date()));
            if (sum != null) {
                sum[2] = sum[2].add(row.netProfit());
            }
        }
        List<EquityPeriod> periods = new ArrayList<>();
        BigDecimal running = opening();
        for (Map.Entry<LocalDate, BigDecimal[]> entry : sums.entrySet()) {
            BigDecimal[] sum = entry.getValue();
            running = running.add(sum[0]).subtract(sum[1]).add(sum[2]);
            periods.add(new EquityPeriod(entry.getKey(), granularity.label(entry.getKey()), sum[0], sum[1], sum[2],
                    running));
        }
        return periods;
    }
}
