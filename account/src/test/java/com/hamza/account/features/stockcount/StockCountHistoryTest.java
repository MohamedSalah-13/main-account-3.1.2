package com.hamza.account.features.stockcount;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The count history's filter, page and statements - the decisions, apart from the screen and the database. */
class StockCountHistoryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("the history opens on the year so far - a shop counts a few times a year, not a month")
    void theOpeningFilter() {
        StockCountHistoryFilter filter = StockCountHistoryFilter.thisYear(DAY);

        assertEquals(LocalDate.of(2026, 1, 1), filter.from());
        assertEquals(DAY, filter.to());
        assertNull(filter.stockId());
        assertNull(filter.status());
        assertEquals(0, filter.page());
    }

    @Test
    @DisplayName("a reversed period, a negative page and an absurd page size are refused")
    void whatAFilterRefuses() {
        assertThrows(IllegalArgumentException.class,
                () -> new StockCountHistoryFilter(DAY, DAY.minusDays(1), null, null, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new StockCountHistoryFilter(DAY, DAY, null, null, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> new StockCountHistoryFilter(DAY, DAY, null, null, 0, 0));
        assertThrows(NullPointerException.class, () -> new StockCountHistoryFilter(null, DAY, null, null, 0, 50));
    }

    /** A draft has moved nothing, so a variance that read one would report a shortage never booked. */
    @Test
    @DisplayName("the variance reads the same period and warehouse, posted sheets only, from the first row")
    void theVarianceIsPostedOnly() {
        StockCountHistoryFilter variance = new StockCountHistoryFilter(DAY, DAY, 3, StockCountStatus.DRAFT, 4, 50)
                .postedOnly();

        assertEquals(StockCountStatus.POSTED, variance.status());
        assertEquals(3, variance.stockId());
        assertEquals(0, variance.page());
        assertEquals(StockCountHistoryFilter.PRINT_LIMIT, variance.pageSize());
    }

    @Test
    @DisplayName("a page reads one row more than it shows, and the totals are the whole set's")
    void theExtraRowIsCut() {
        StockCountHistoryFilter filter = new StockCountHistoryFilter(DAY, DAY, null, null, 1, 2);

        StockCountHistoryPage full = StockCountHistoryPage.of(List.of(row(1), row(2), row(3)), 9, 40, 7, filter);

        assertEquals(List.of(row(1), row(2)), full.rows());
        assertTrue(full.hasNext());
        assertTrue(full.hasPrevious());
        assertEquals(9, full.sheets());
        assertEquals(40, full.lines());
        assertEquals(7, full.differences());
        assertFalse(StockCountHistoryPage.of(List.of(row(1)), 1, 1, 0,
                StockCountHistoryFilter.thisYear(DAY)).hasPrevious());
    }

    @Test
    @DisplayName("the page, its totals and the variance read one WHERE")
    void oneWhereForThree() {
        for (String statement : new String[]{StockCountHistoryQuery.PAGE, StockCountHistoryQuery.TOTALS,
                StockCountHistoryQuery.VARIANCE}) {
            assertTrue(statement.contains(StockCountHistoryQuery.WHERE), statement);
        }
    }

    @Test
    @DisplayName("each statement binds the WHERE's values and exactly its own after them")
    void parameterCounts() {
        int where = placeholders(StockCountHistoryQuery.WHERE);
        assertEquals(where, StockCountHistoryQuery.whereValues(StockCountHistoryFilter.thisYear(DAY)).length);
        assertEquals(where + 2, placeholders(StockCountHistoryQuery.PAGE), "LIMIT and OFFSET");
        assertEquals(where, placeholders(StockCountHistoryQuery.TOTALS));
        assertEquals(where + 1, placeholders(StockCountHistoryQuery.VARIANCE), "LIMIT");
        assertEquals(1, placeholders(StockCountHistoryQuery.HEADER));
    }

    @Test
    @DisplayName("the warehouse and the status bind in their places; none binds null")
    void theValuesInTheirPlaces() {
        assertArrayEquals(new Object[]{Date.valueOf(DAY), Date.valueOf(DAY), 4, 4, "POSTED", "POSTED"},
                StockCountHistoryQuery.whereValues(new StockCountHistoryFilter(DAY, DAY, 4,
                        StockCountStatus.POSTED, 0, 50)));
        assertArrayEquals(new Object[]{Date.valueOf(DAY), Date.valueOf(DAY), null, null, null, null},
                StockCountHistoryQuery.whereValues(new StockCountHistoryFilter(DAY, DAY, null, null, 0, 50)));
    }

    /**
     * What the history calls a difference and what the balance was moved by are one thing: the
     * expression is read out of the view that moves the balance, not typed a second time.
     */
    @Test
    @DisplayName("a difference is adjustment_agg's own expression, read out of R__views.sql")
    void aDifferenceIsTheViewsOwn() throws IOException {
        String views = Files.readString(Path.of("src", "main", "resources", "db", "migration", "R__views.sql"),
                StandardCharsets.UTF_8);

        assertTrue(views.contains("SUM(" + StockCountHistoryQuery.DIFFERENCE + ")"),
                "adjustment_agg no longer sums " + StockCountHistoryQuery.DIFFERENCE);
    }

    /**
     * An item on two lines of an old sheet must contribute what that sheet moved it by, once - so the
     * variance sums per sheet and item before splitting into surplus and shortage.
     */
    @Test
    @DisplayName("the variance sums a sheet's lines of one item before it splits surplus from shortage")
    void theVarianceSumsPerSheetFirst() {
        assertTrue(StockCountHistoryQuery.VARIANCE.contains("GROUP BY c.id, scl.item_id) per"));
        assertTrue(StockCountHistoryQuery.VARIANCE.contains("SUM(GREATEST(per.diff, 0))"));
    }

    private static StockCountSummary row(int id) {
        return new StockCountSummary(id, DAY, 1, "main", StockCountStatus.POSTED, null, null, "admin", 3, 1);
    }

    private static int placeholders(String sql) {
        int count = 0;
        for (int at = sql.indexOf('?'); at >= 0; at = sql.indexOf('?', at + 1)) {
            count++;
        }
        return count;
    }
}
