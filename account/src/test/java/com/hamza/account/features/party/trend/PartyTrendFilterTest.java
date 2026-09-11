package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.trend.PartyTrendFilter.Problem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTrendFilterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    @Test
    void aScreenCanAskWhatIsWrongBeforeBuildingOne() {
        assertEquals(Problem.NONE, PartyTrendFilter.problem(TrendGranularity.MONTH,
                LocalDate.of(2026, 1, 1), TODAY));
        assertEquals(Problem.REVERSED, PartyTrendFilter.problem(TrendGranularity.MONTH,
                TODAY, LocalDate.of(2026, 1, 1)));
        // Three years by the week is past the ceiling; the same three years by the month is not.
        assertEquals(Problem.TOO_MANY_PERIODS, PartyTrendFilter.problem(TrendGranularity.WEEK,
                LocalDate.of(2023, 9, 1), TODAY));
        assertEquals(Problem.NONE, PartyTrendFilter.problem(TrendGranularity.MONTH,
                LocalDate.of(2023, 9, 1), TODAY));
    }

    @Test
    void theRecordRefusesWhatCannotBeCharted() {
        assertThrows(IllegalArgumentException.class, () -> new PartyTrendFilter(PartyKind.CUSTOMER,
                TrendGranularity.MONTH, TODAY, LocalDate.of(2026, 1, 1), null, false));
        assertThrows(IllegalArgumentException.class, () -> new PartyTrendFilter(PartyKind.CUSTOMER,
                TrendGranularity.WEEK, LocalDate.of(2020, 1, 1), TODAY, null, false));
        assertThrows(IllegalArgumentException.class, () -> new PartyTrendFilter(null,
                TrendGranularity.MONTH, LocalDate.of(2026, 1, 1), TODAY, null, false));
        assertThrows(IllegalArgumentException.class, () -> new PartyTrendFilter(PartyKind.CUSTOMER,
                TrendGranularity.MONTH, LocalDate.of(2026, 1, 1), TODAY, 0, false));
    }

    @Test
    void aYearlyChartIsNeverComparedWithTheYearBefore() {
        PartyTrendFilter yearly = new PartyTrendFilter(PartyKind.SUPPLIER, TrendGranularity.YEAR,
                LocalDate.of(2022, 1, 1), TODAY, null, true);
        PartyTrendFilter monthly = new PartyTrendFilter(PartyKind.SUPPLIER, TrendGranularity.MONTH,
                LocalDate.of(2026, 1, 1), TODAY, null, true);

        assertFalse(yearly.compareWithPreviousYear());
        assertTrue(monthly.compareWithPreviousYear());
    }

    @Test
    void theComparisonIsTheSameDatesAYearBack() {
        PartyTrendFilter filter = new PartyTrendFilter(PartyKind.CUSTOMER, TrendGranularity.MONTH,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 7, true);

        assertEquals(LocalDate.of(2025, 3, 1), filter.previousFrom());
        assertEquals(LocalDate.of(2025, 3, 31), filter.previousTo());
    }

    @Test
    void theDefaultIsEveryPartyOverTheGroupingsOwnRange() {
        PartyTrendFilter filter = PartyTrendFilter.defaultFor(PartyKind.CUSTOMER, TrendGranularity.MONTH, TODAY);

        assertEquals(LocalDate.of(2025, 10, 1), filter.from());
        assertEquals(TODAY, filter.to());
        assertNull(filter.partyId());
        assertFalse(filter.compareWithPreviousYear());
    }
}
