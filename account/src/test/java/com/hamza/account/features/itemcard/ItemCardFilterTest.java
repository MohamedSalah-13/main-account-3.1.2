package com.hamza.account.features.itemcard;

import com.hamza.account.type.ProcessType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemCardFilterTest {

    @Test
    void refusesAMissingOrReversedPeriodWithAUserMessageKey() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new ItemCardFilter(null, LocalDate.of(2026, 9, 15), null));
        IllegalArgumentException reversed = assertThrows(IllegalArgumentException.class,
                () -> new ItemCardFilter(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 15), null));

        assertEquals("item.card.date.required", missing.getMessage());
        assertEquals("item.card.date.range.invalid", reversed.getMessage());
    }

    @Test
    void keepsTheOptionalDocumentKind() {
        ItemCardFilter filter = new ItemCardFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), ProcessType.SALES);

        assertEquals(ProcessType.SALES, filter.processType());
        assertNull(new ItemCardFilter(filter.from(), filter.to(), null).processType());
    }

    @Test
    void quickPeriodsUseTodayAndTheCalendarMonth() {
        LocalDate today = LocalDate.of(2026, 9, 15);

        assertEquals(new ItemCardFilter.DateRange(today, today), ItemCardFilter.today(today));
        assertEquals(new ItemCardFilter.DateRange(LocalDate.of(2026, 9, 1), today),
                ItemCardFilter.monthToDate(today));
    }

    @Test
    void wholeHistoryStartsAtTheFirstMovementOrTodayWhenThereIsNone() {
        LocalDate today = LocalDate.of(2026, 9, 15);
        LocalDate first = LocalDate.of(2024, 2, 3);

        assertEquals(new ItemCardFilter.DateRange(first, today),
                ItemCardFilter.wholeHistory(first, today));
        assertEquals(new ItemCardFilter.DateRange(today, today),
                ItemCardFilter.wholeHistory(null, today));
    }
}
