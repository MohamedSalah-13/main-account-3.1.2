package com.hamza.account.features.stocktransfer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transfer history's statements: one {@code WHERE} in all three, and the values bound to it in
 * the order it reads them. A statement bound with the wrong count fails; one bound in the wrong
 * order filters by the wrong thing and fails nothing - which is what the order assertions are for.
 */
class StockTransferHistoryQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("the page, its totals and the printed log read one WHERE")
    void oneWhereForThree() {
        for (String statement : new String[]{StockTransferHistoryQuery.PAGE, StockTransferHistoryQuery.TOTALS,
                StockTransferHistoryQuery.LOG}) {
            assertTrue(statement.contains(StockTransferHistoryQuery.WHERE),
                    "a statement that narrows the set its own way describes other transfers than the list:\n" + statement);
        }
    }

    @Test
    @DisplayName("each statement binds the WHERE's values and exactly its own after them")
    void parameterCounts() {
        int where = placeholders(StockTransferHistoryQuery.WHERE);
        assertEquals(where, StockTransferHistoryQuery.whereValues(filter(null, null)).length);
        assertEquals(where + 2, placeholders(StockTransferHistoryQuery.PAGE), "LIMIT and OFFSET");
        assertEquals(where, placeholders(StockTransferHistoryQuery.TOTALS));
        assertEquals(where + 1, placeholders(StockTransferHistoryQuery.LOG), "LIMIT");
        assertEquals(1, placeholders(StockTransferHistoryQuery.HEADER));
        assertEquals(1, placeholders(StockTransferHistoryQuery.LINES));
    }

    @Test
    @DisplayName("no warehouse and no text bind nulls, which the WHERE reads as no condition")
    void anUnnarrowedFilterBindsNulls() {
        assertArrayEquals(new Object[]{Date.valueOf(FROM), Date.valueOf(TO), null, null, null,
                        null, null, null, null, null, null},
                StockTransferHistoryQuery.whereValues(filter(null, null)));
    }

    /**
     * The warehouse three times - once for "is there one" and once for each end - and the text as a
     * pattern where the WHERE says LIKE and as itself where it says {@code =}: a code is matched
     * exactly, since a partial code matches half the catalogue.
     */
    @Test
    @DisplayName("the warehouse matches either end, the text by pattern for names and exactly for codes")
    void theValuesInTheirPlaces() {
        assertArrayEquals(new Object[]{Date.valueOf(FROM), Date.valueOf(TO), 4, 4, 4,
                        "6221", "%6221%", "%6221%", "6221", "6221", "6221"},
                StockTransferHistoryQuery.whereValues(filter(4, "6221")));
    }

    @Test
    @DisplayName("a percent sign typed into the box is a percent sign, not everything")
    void wildcardsAreEscaped() {
        assertEquals("%50!%%", StockTransferHistoryQuery.pattern("50%"));
        assertEquals("%a!_b%", StockTransferHistoryQuery.pattern("a_b"));
        assertEquals("%!!%", StockTransferHistoryQuery.pattern("!"));
        assertEquals(2, countOf(StockTransferHistoryQuery.WHERE, "LIKE ? ESCAPE '!'"),
                "both LIKEs name the escape the pattern uses");
    }

    /** {@code a OR b AND c} is {@code a OR (b AND c)}: unbracketed, a note would let every warehouse through. */
    @Test
    @DisplayName("the warehouse pair and the text alternatives are each inside one bracket")
    void theAlternativesAreBracketed() {
        assertTrue(StockTransferHistoryQuery.WHERE.contains("AND (? IS NULL OR t.stock_from = ? OR t.stock_to = ?)"));
        assertTrue(StockTransferHistoryQuery.WHERE.contains("AND (? IS NULL\n       OR t.notes LIKE ? ESCAPE '!'"));
    }

    @Test
    @DisplayName("a transfer is one row of the page however many lines it carries")
    void thePageDoesNotMultiplyTransfers() {
        assertFalse(StockTransferHistoryQuery.PAGE.contains("GROUP BY"),
                "the line count is a subquery - a join and a GROUP BY drops a transfer with no lines");
        assertTrue(StockTransferHistoryQuery.TOTALS.contains("COUNT(DISTINCT t.id)"),
                "the totals join the lines, so the transfers are counted distinct");
        assertTrue(StockTransferHistoryQuery.TOTALS.contains("LEFT JOIN stock_transfer_list"),
                "an inner join would count a transfer with no lines in the page and not in the total");
    }

    private static StockTransferHistoryFilter filter(Integer stockId, String text) {
        return new StockTransferHistoryFilter(FROM, TO, stockId, text, 0, 50);
    }

    private static int placeholders(String sql) {
        return countOf(sql, "?");
    }

    private static int countOf(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }
}
