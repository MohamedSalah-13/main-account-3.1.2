package com.hamza.account.features.stockopening;

import com.hamza.account.features.items.WarehouseOpeningBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The statements behind the opening-balances screen, and what each binds. */
class WarehouseOpeningQueryTest {

    private static long marks(String sql) {
        return sql.chars().filter(c -> c == '?').count();
    }

    @Test
    @DisplayName("the page and its count are read over one WHERE")
    void onePredicate() {
        assertTrue(WarehouseOpeningQuery.PAGE.contains(WarehouseOpeningQuery.FROM + WarehouseOpeningQuery.WHERE));
        assertTrue(WarehouseOpeningQuery.COUNT.endsWith(WarehouseOpeningQuery.FROM + WarehouseOpeningQuery.WHERE));
    }

    /** A row the screen offers as open must be one the save accepts - one text, not two queries agreeing. */
    @Test
    @DisplayName("whether a row has moved is the rule's own condition")
    void movedIsTheRulesCondition() {
        assertTrue(WarehouseOpeningQuery.PAGE.contains(WarehouseOpeningBalance.MOVED + " AS moved"));
        assertTrue(WarehouseOpeningQuery.WHERE.contains("NOT " + WarehouseOpeningBalance.MOVED));
    }

    @Test
    @DisplayName("every mark has its value: the WHERE's, then the limit and the offset")
    void everyMarkIsBound() {
        WarehouseOpeningFilter filter = new WarehouseOpeningFilter(2, "علبة", true, 3, 100);
        Object[] where = WarehouseOpeningQuery.whereValues(filter);

        assertEquals(marks(WarehouseOpeningQuery.WHERE), where.length);
        assertEquals(marks(WarehouseOpeningQuery.COUNT), where.length);
        assertEquals(marks(WarehouseOpeningQuery.PAGE), where.length + 2L);
        assertArrayEquals(new Object[]{2, "علبة", "%علبة%", "علبة", "علبة", "علبة", 1}, where);
        assertEquals(300, filter.offset());
        assertEquals(101, filter.queryLimit());
    }

    @Test
    @DisplayName("no text is no condition, and every item is listed unless asked otherwise")
    void noTextNoCondition() {
        Object[] where = WarehouseOpeningQuery.whereValues(WarehouseOpeningFilter.firstPage(2, "  ", false));

        assertArrayEquals(new Object[]{2, null, null, null, null, null, 0}, where);
    }

    @Test
    @DisplayName("a % or _ typed into the search is literal")
    void wildcardsAreEscaped() {
        assertEquals("%50!%!_off!!%", WarehouseOpeningQuery.pattern("50%_off!"));
    }

    @Test
    @DisplayName("a filter names one warehouse, and a sane page")
    void theFilterChecksItself() {
        assertThrows(IllegalArgumentException.class, () -> WarehouseOpeningFilter.firstPage(0, null, false));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseOpeningFilter(2, null, false, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseOpeningFilter(2, null, false, 0, 0));
    }

    @Test
    @DisplayName("the page cuts the extra row it read and says there is more")
    void thePageCutsTheExtraRow() {
        WarehouseOpeningFilter filter = new WarehouseOpeningFilter(2, null, false, 1, 2);
        var fetched = java.util.List.of(
                new WarehouseOpeningRow(1, "a", "a", "u", 0, false),
                new WarehouseOpeningRow(2, "b", "b", "u", 0, false),
                new WarehouseOpeningRow(3, "c", "c", "u", 0, true));

        WarehouseOpeningPage page = WarehouseOpeningPage.of(fetched, 9, filter);

        assertEquals(2, page.rows().size());
        assertTrue(page.hasNext());
        assertTrue(page.hasPrevious());
        assertEquals(9, page.items());
    }
}
