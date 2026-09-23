package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.party.trend.TrendGranularity;

import java.time.LocalDate;
import java.util.Objects;

/**
 * How the statement's table files days into rows: one a day, a week or a month.
 *
 * <p>A week and a month are {@link TrendGranularity}'s - the week starts on a Saturday, as on every
 * statement and chart here - so this adds the day and nothing else. A row is always cut at the period's
 * own edges: a month grouping over the 10th of March to the 20th of May has a first row of the 10th to
 * the 31st, and says so.</p>
 */
public enum ProfitLossGrouping {

    DAY("profitloss.grouping.day"),
    WEEK("profitloss.grouping.week"),
    MONTH("profitloss.grouping.month");

    private final String messageKey;

    ProfitLossGrouping(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    /** The first day of the row {@code day} falls in, before it is cut at the period's start. */
    public LocalDate start(LocalDate day) {
        Objects.requireNonNull(day, "day");
        return switch (this) {
            case DAY -> day;
            case WEEK -> TrendGranularity.WEEK.start(day);
            case MONTH -> TrendGranularity.MONTH.start(day);
        };
    }

    /** The first day of the row after the one starting on {@code start}. */
    public LocalDate next(LocalDate start) {
        return switch (this) {
            case DAY -> start.plusDays(1);
            case WEEK -> TrendGranularity.WEEK.next(start);
            case MONTH -> TrendGranularity.MONTH.next(start);
        };
    }

    /** The grouping a period opens with: a day to a row up to two months, a week to a row up to a half year. */
    public static ProfitLossGrouping suitedTo(ProfitLossPeriod period) {
        long days = period.days();
        if (days <= 62) {
            return DAY;
        }
        return days <= 190 ? WEEK : MONTH;
    }
}
