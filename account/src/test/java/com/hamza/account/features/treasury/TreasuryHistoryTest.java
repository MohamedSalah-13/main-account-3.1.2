package com.hamza.account.features.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The filter and the page of a treasury history list - the decisions, apart from the screen and the database. */
class TreasuryHistoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 18);

    @Test
    @DisplayName("the screen opens on the month so far, every treasury, the first page")
    void theOpeningFilter() {
        TreasuryHistoryFilter filter = TreasuryHistoryFilter.thisMonth(DAY);

        assertEquals(LocalDate.of(2026, 9, 1), filter.from());
        assertEquals(DAY, filter.to());
        assertEquals(0, filter.page());
        assertEquals(0, filter.panelConditionCount(), "the period says which list this is; it narrows nothing");
    }

    @Test
    @DisplayName("a reversed period, a negative page and an absurd page size are refused")
    void whatAFilterRefuses() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreasuryHistoryFilter(DAY, DAY.minusDays(1), null, null, 0, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new TreasuryHistoryFilter(DAY, DAY, null, null, -1, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new TreasuryHistoryFilter(DAY, DAY, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TreasuryHistoryFilter(DAY, DAY, null, null, 0, TreasuryHistoryFilter.PRINT_LIMIT + 1));
        assertThrows(NullPointerException.class,
                () -> new TreasuryHistoryFilter(null, DAY, null, null, 0, 50));
    }

    @Test
    @DisplayName("the treasury and the direction are counted as conditions; one day is a period")
    void conditionsAreCounted() {
        assertEquals(2, new TreasuryHistoryFilter(DAY, DAY, 3, CashDirection.DEPOSIT, 0, 50).panelConditionCount());
        assertEquals(1, new TreasuryHistoryFilter(DAY, DAY, 3, null, 0, 50).panelConditionCount());
    }

    @Test
    @DisplayName("a page reads one row more than it shows, and paging keeps the filter")
    void pagingArithmetic() {
        TreasuryHistoryFilter third = new TreasuryHistoryFilter(DAY, DAY, 3, CashDirection.WITHDRAWAL, 0, 50).onPage(2);

        assertEquals(100, third.offset());
        assertEquals(51, third.queryLimit());
        assertEquals(3, third.treasuryId());
        assertEquals(CashDirection.WITHDRAWAL, third.direction());
    }

    @Test
    @DisplayName("printing reads the whole filtered set from its first row, not the page on screen")
    void printingIgnoresThePage() {
        TreasuryHistoryFilter print = new TreasuryHistoryFilter(DAY, DAY, 3, null, 4, 50).forPrint();

        assertEquals(0, print.page());
        assertEquals(TreasuryHistoryFilter.PRINT_LIMIT, print.pageSize());
        assertEquals(3, print.treasuryId());
    }

    @Test
    @DisplayName("the extra row says there is a next page, and is not shown")
    void theExtraRowIsCut() {
        TreasuryHistoryFilter filter = new TreasuryHistoryFilter(DAY, DAY, null, null, 1, 2);

        TreasuryHistoryPage<String> full = TreasuryHistoryPage.of(List.of("a", "b", "c"), totals(), filter);
        TreasuryHistoryPage<String> last = TreasuryHistoryPage.of(List.of("a", "b"), totals(), filter);

        assertEquals(List.of("a", "b"), full.rows());
        assertTrue(full.hasNext());
        assertTrue(full.hasPrevious());
        assertFalse(full.truncated());
        assertFalse(last.hasNext());
    }

    @Test
    @DisplayName("a print extract that met the limit is truncated, not paged")
    void aFullPrintExtractIsTruncated() {
        TreasuryHistoryFilter print = TreasuryHistoryFilter.thisMonth(DAY).forPrint();
        List<Integer> fetched = new ArrayList<>();
        for (int i = 0; i <= TreasuryHistoryFilter.PRINT_LIMIT; i++) fetched.add(i);

        TreasuryHistoryPage<Integer> extract = TreasuryHistoryPage.of(fetched, totals(), print);

        assertTrue(extract.truncated());
        assertFalse(extract.hasNext());
        assertEquals(TreasuryHistoryFilter.PRINT_LIMIT, extract.rows().size());
    }

    @Test
    @DisplayName("totals of nothing are zero, never null")
    void emptyTotalsAreZero() {
        TreasuryHistoryPage.Totals none = new TreasuryHistoryPage.Totals(0, null, null);

        assertEquals(0, none.first().signum());
        assertEquals(0, none.second().signum());
    }

    private static TreasuryHistoryPage.Totals totals() {
        return new TreasuryHistoryPage.Totals(3, null, null);
    }
}
