package com.hamza.account.features.party.profile;

import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.party.trend.TrendGranularity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One party's profile over a period, and every view of it the screen shows.
 *
 * <p><b>It defines nothing.</b> The items and the days are read by {@link PartyProfileQuery}; what
 * is done here is arranging them - by group, by period, by weekday, and against the period before -
 * with no JavaFX, so each arrangement is tested. The periods are {@link TrendGranularity}'s, and the
 * week starts on {@link StatementPeriod#FIRST_DAY_OF_WEEK}: one definition of a week for the
 * statement, the trend chart and this.</p>
 *
 * @param balance what the party owes today, for a reader who may see accounts; empty otherwise
 */
public record PartyProfile(PartyProfileFilter filter, PartyProfileSummary summary, List<PartyItemRow> items,
                           List<PartyItemRow> previousItems, List<PartyProfileDay> days,
                           Optional<BigDecimal> balance) {

    /** The most periods one chart draws - the trend chart's ceiling, for the same reason. */
    public static final int MAX_PERIODS = 120;

    public PartyProfile {
        items = List.copyOf(items);
        previousItems = List.copyOf(previousItems);
        days = List.copyOf(days);
    }

    public static PartyProfile build(PartyProfileFilter filter, List<PartyItemRow> items,
                                     List<PartyItemRow> previousItems, List<PartyProfileDay> days,
                                     Optional<LocalDate> lastEver, Optional<BigDecimal> balance) {
        return new PartyProfile(filter, PartyProfileSummary.of(days, items, lastEver), items, previousItems,
                days, balance);
    }

    public boolean isEmpty() {
        return items.isEmpty() && days.isEmpty();
    }

    /** An item's part of the lines' net, or empty when the lines come to nothing to divide by. */
    public Optional<BigDecimal> share(PartyItemRow item) {
        return percentOf(item.net(), summary.itemsNet());
    }

    /** The items gathered by their group, the largest first. */
    public List<PartyGroupRow> groups() {
        Map<Integer, PartyGroupRow> byGroup = new LinkedHashMap<>();
        for (PartyItemRow item : items) {
            byGroup.merge(item.groupId(), new PartyGroupRow(item.groupId(), item.groupName(), 1, item.net()),
                    (was, one) -> new PartyGroupRow(was.groupId(), was.groupName(), was.items() + 1,
                            was.net().add(one.net())));
        }
        return byGroup.values().stream()
                .sorted(Comparator.comparing(PartyGroupRow::net).reversed()
                        .thenComparing(PartyGroupRow::groupName))
                .toList();
    }

    public Optional<BigDecimal> share(PartyGroupRow group) {
        return percentOf(group.net(), summary.itemsNet());
    }

    /** Whether the period can be drawn by this grouping without the points running together. */
    public boolean canGroupBy(TrendGranularity granularity) {
        return granularity.periods(filter.from(), filter.to()) <= MAX_PERIODS;
    }

    /**
     * Every period of the range, the empty ones included - a month with nothing bought is the
     * point the chart is opened to see, and leaving it out would draw a line straight over it.
     */
    public List<PartyProfilePeriod> periods(TrendGranularity granularity) {
        if (!canGroupBy(granularity)) {
            throw new IllegalArgumentException("too many " + granularity + " periods between "
                    + filter.from() + " and " + filter.to());
        }
        Map<LocalDate, int[]> counts = new TreeMap<>();
        Map<LocalDate, BigDecimal> nets = new TreeMap<>();
        for (LocalDate start = granularity.start(filter.from()); !start.isAfter(filter.to());
             start = granularity.next(start)) {
            counts.put(start, new int[1]);
            nets.put(start, BigDecimal.ZERO);
        }
        for (PartyProfileDay day : days) {
            LocalDate start = granularity.start(day.day());
            counts.get(start)[0] += day.documents();
            nets.merge(start, day.net(), BigDecimal::add);
        }
        List<PartyProfilePeriod> periods = new ArrayList<>();
        nets.forEach((start, net) -> periods.add(
                new PartyProfilePeriod(start, granularity.label(start), counts.get(start)[0], net)));
        return periods;
    }

    /** The seven days of the week from Saturday, each with what fell on it across the whole period. */
    public List<PartyWeekday> weekdays() {
        Map<DayOfWeek, int[]> counts = new LinkedHashMap<>();
        Map<DayOfWeek, BigDecimal> nets = new LinkedHashMap<>();
        DayOfWeek day = StatementPeriod.FIRST_DAY_OF_WEEK;
        for (int index = 0; index < 7; index++, day = day.plus(1)) {
            counts.put(day, new int[1]);
            nets.put(day, BigDecimal.ZERO);
        }
        for (PartyProfileDay one : days) {
            DayOfWeek weekday = one.day().getDayOfWeek();
            counts.get(weekday)[0] += one.documents();
            nets.merge(weekday, one.net(), BigDecimal::add);
        }
        List<PartyWeekday> weekdays = new ArrayList<>();
        nets.forEach((weekday, net) -> weekdays.add(new PartyWeekday(weekday, counts.get(weekday)[0], net)));
        return weekdays;
    }

    /**
     * What the party took in the period before and has not taken in this one, the largest first.
     * An item bought and returned in full nets to nothing and was not really bought.
     */
    public List<PartyLapsedItem> lapsed() {
        Map<Integer, BigDecimal> now = new LinkedHashMap<>();
        for (PartyItemRow item : items) {
            now.put(item.itemId(), item.quantity());
        }
        return previousItems.stream()
                .filter(before -> before.quantity().signum() > 0)
                .filter(before -> now.getOrDefault(before.itemId(), BigDecimal.ZERO).signum() <= 0)
                .map(before -> new PartyLapsedItem(before.itemId(), before.itemName(), before.unitName(),
                        before.groupName(), before.quantity(), before.net(), before.documents()))
                .sorted(Comparator.comparing(PartyLapsedItem::previousNet).reversed()
                        .thenComparing(PartyLapsedItem::itemName))
                .toList();
    }

    private static Optional<BigDecimal> percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP));
    }
}
