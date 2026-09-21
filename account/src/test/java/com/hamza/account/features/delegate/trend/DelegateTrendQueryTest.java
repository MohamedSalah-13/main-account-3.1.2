package com.hamza.account.features.delegate.trend;

import com.hamza.account.features.delegate.DelegateActivityQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trend's figures are the performance report's, and this is what holds them so: every sum and
 * every date condition the daily query uses is found, as text, in the activity query. A chart that
 * disagrees with the row it was opened from is the defect the party and delegate work each exist to
 * remove - two statements of one rule.
 */
class DelegateTrendQueryTest {

    private static final String ACTIVITY = DelegateActivityQuery.ACTIVITY_SQL;

    @ParameterizedTest
    @ValueSource(strings = {DelegateTrendQuery.NET, DelegateTrendQuery.CASH_ON_SALES,
            DelegateTrendQuery.CASH_REFUNDED, DelegateTrendQuery.COLLECTED})
    @DisplayName("each sum is the activity query's own")
    void eachSumIsTheActivityQuerys(String sum) {
        assertTrue(ACTIVITY.contains(sum), sum + " is not in ACTIVITY_SQL - the two have drifted");
        assertTrue(DelegateTrendQuery.DAILY_SQL.contains(sum), sum);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoice_date BETWEEN ? AND ?", "account_date BETWEEN ? AND ?"})
    @DisplayName("each period is bounded by the same column the activity query bounds")
    void theSameDateColumns(String condition) {
        assertTrue(ACTIVITY.contains(condition), condition);
        assertTrue(DelegateTrendQuery.DAILY_SQL.contains(condition), condition);
    }

    /** Collected is cash in both directions: what was refunded on a return is taken off. */
    @Test
    void cashRefundedOnAReturnIsTakenOff() {
        assertTrue(DelegateTrendQuery.DAILY_SQL.contains("-" + DelegateTrendQuery.CASH_REFUNDED));
        assertTrue(ACTIVITY.contains("- COALESCE(r.cash, 0)"), "the activity query subtracts it too");
    }

    /** A credit note is {@code customers_accounts.purchase} and is nobody's collection. */
    @Test
    void aCreditNoteIsNotRead() {
        assertFalse(DelegateTrendQuery.DAILY_SQL.contains("purchase"));
    }

    @Test
    @DisplayName("nine placeholders: the delegate and the two dates, three times")
    void parameterCount() {
        assertEquals(9, DelegateTrendQuery.DAILY_SQL.chars().filter(c -> c == '?').count());
        assertEquals(3, DelegateTrendQuery.DAILY_SQL.split("delegate_id = \\?", -1).length - 1);
    }

    /** Grouped by day on each side before adding, as the activity query groups before its join. */
    @Test
    void eachSourceIsGroupedOnItsOwn() {
        assertEquals(2, occurrences("GROUP BY invoice_date"), "a sale and a return, each by day");
        assertEquals(1, occurrences("GROUP BY account_date"), "the collections, by day");
        assertTrue(DelegateTrendQuery.DAILY_SQL.contains("GROUP BY movements.day"));
    }

    private static int occurrences(String text) {
        return DelegateTrendQuery.DAILY_SQL.split(java.util.regex.Pattern.quote(text), -1).length - 1;
    }
}
