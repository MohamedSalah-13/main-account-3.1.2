package com.hamza.account.features.report.summary;

import com.hamza.account.features.profitloss.statement.ComparisonBasis;
import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryPeriodTest {

    /** A Wednesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Test
    @DisplayName("the week starts on Saturday, as on every statement, not on Monday")
    void theWeekStartsOnSaturday() {
        ProfitLossPeriod week = SummaryPeriod.WEEK.range(TODAY);

        assertEquals(LocalDate.of(2026, 9, 19), week.from());
        assertEquals(DayOfWeek.SATURDAY, week.from().getDayOfWeek());
        assertEquals(TODAY, week.to());
        LocalDate saturday = LocalDate.of(2026, 9, 19);
        assertEquals(new ProfitLossPeriod(saturday, saturday), SummaryPeriod.WEEK.range(saturday),
                "on a Saturday the week is that one day");
    }

    @Test
    @DisplayName("today and this month end today; this month is set against the same days of the last")
    void todayAndTheMonth() {
        assertEquals(new ProfitLossPeriod(TODAY, TODAY), SummaryPeriod.TODAY.range(TODAY));
        ProfitLossPeriod month = SummaryPeriod.MONTH.range(TODAY);
        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 9, 1), TODAY), month);
        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 23)),
                SummaryPeriod.previousOf(SummaryPeriod.MONTH, month),
                "the 1st to the 23rd of August, not the 23 days before the 1st of September");
        assertEquals(new ProfitLossPeriod(TODAY.minusDays(1), TODAY.minusDays(1)),
                SummaryPeriod.previousOf(SummaryPeriod.TODAY, SummaryPeriod.TODAY.range(TODAY)), "yesterday");
    }

    @Test
    @DisplayName("the week so far is set against the same days of the week before, the weekend included")
    void theWeekIsSetAgainstTheSameDaysOfTheWeekBefore() {
        ProfitLossPeriod week = SummaryPeriod.WEEK.range(TODAY);

        ProfitLossPeriod before = SummaryPeriod.previousOf(SummaryPeriod.WEEK, week);

        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 16)), before,
                "Saturday to Wednesday, not the Monday to Friday straight before it");
        assertEquals(DayOfWeek.SATURDAY, before.from().getDayOfWeek());
    }

    @Test
    @DisplayName("the reader's own dates follow the profit and loss's rule")
    void ownDatesFollowTheProfitAndLoss() {
        ProfitLossPeriod tenDays = new ProfitLossPeriod(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19));

        assertEquals(tenDays.previous(ComparisonBasis.PREVIOUS_PERIOD), SummaryPeriod.previousOf(null, tenDays));
        assertEquals(new ProfitLossPeriod(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 9)),
                SummaryPeriod.previousOf(null, tenDays));
    }
}
