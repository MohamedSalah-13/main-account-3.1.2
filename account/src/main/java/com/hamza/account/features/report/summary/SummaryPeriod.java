package com.hamza.account.features.report.summary;

import com.hamza.account.features.party.statement.StatementPeriod;
import com.hamza.account.features.profitloss.statement.ComparisonBasis;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;

import java.time.LocalDate;

/**
 * The summary's three ready periods, each ending today.
 *
 * <p><b>The week starts on Saturday</b> ({@link StatementPeriod#FIRST_DAY_OF_WEEK}); the summary's own
 * arithmetic started it on Monday, so Saturday's and Sunday's takings - two of an Arabic-market shop's
 * busiest days - were counted in the week before on this screen and in this week on every other. A period
 * of the reader's own dates is a {@link ProfitLossPeriod} built from the pickers.</p>
 *
 * <p><b>What a period is compared with</b> ({@link #previousOf}): the profit and loss's rule - the same days
 * of the month before for a period that starts on the 1st, the same number of days straight before
 * otherwise - except for the week so far, which is set against <b>the same days of the week before</b>.
 * Saturday to Wednesday against the five days straight before it would be Monday to Friday: two quiet
 * weekdays in place of the weekend's two busiest days.</p>
 */
public enum SummaryPeriod {

    TODAY,
    WEEK,
    MONTH;

    public ProfitLossPeriod range(LocalDate today) {
        return switch (this) {
            case TODAY -> new ProfitLossPeriod(today, today);
            case WEEK -> new ProfitLossPeriod(StatementPeriod.THIS_WEEK.from(today), today);
            case MONTH -> new ProfitLossPeriod(today.withDayOfMonth(1), today);
        };
    }

    /**
     * The days a period is compared with.
     *
     * @param preset the ready period it is, or null for the reader's own dates
     */
    public static ProfitLossPeriod previousOf(SummaryPeriod preset, ProfitLossPeriod period) {
        if (preset == WEEK) {
            return new ProfitLossPeriod(period.from().minusWeeks(1), period.to().minusWeeks(1));
        }
        return period.previous(ComparisonBasis.PREVIOUS_PERIOD);
    }
}
