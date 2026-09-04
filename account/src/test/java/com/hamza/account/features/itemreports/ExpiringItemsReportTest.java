package com.hamza.account.features.itemreports;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The day boundaries of the expiry report, which are the part that is wrong by one if it is
 * wrong at all: is a batch expiring today "expired", and is one expiring exactly at the end
 * of its warning window inside it?
 */
class ExpiringItemsReportTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);

    private static ExpiringBatch batch(LocalDate expiry, int alertDays, double quantity) {
        return new ExpiringBatch(1, "BC1", "لبن", "ألبان", "قطعة", 10, expiry, quantity, alertDays);
    }

    @Nested
    @DisplayName("what counts as expired")
    class Expired {

        @Test
        @DisplayName("a batch expiring today is not expired yet - it can still be sold today")
        void todayIsStillGood() {
            assertFalse(batch(TODAY, 0, 5).isExpired(TODAY));
            assertTrue(batch(TODAY.minusDays(1), 0, 5).isExpired(TODAY));
        }

        @Test
        @DisplayName("an expired batch is reported however narrow the window, and whatever horizon is set")
        void expiredIsNeverHidden() {
            ExpiringBatch gone = batch(TODAY.minusDays(200), 1, 5);

            assertTrue(ExpiringItemsReport.isFlagged(gone, TODAY, null));
            assertTrue(ExpiringItemsReport.isFlagged(gone, TODAY, TODAY.minusDays(100)),
                    "money already lost must not be hidden by asking about a nearer date");
        }

        @Test
        @DisplayName("the days column counts backwards on an expired batch rather than reading zero")
        void daysGoNegativeOnceThePastIsReached() {
            assertEquals(-11, batch(TODAY.minusDays(11), 0, 5).daysUntil(TODAY));
            assertEquals(0, batch(TODAY, 0, 5).daysUntil(TODAY));
            assertEquals(7, batch(TODAY.plusDays(7), 0, 5).daysUntil(TODAY));
        }
    }

    @Nested
    @DisplayName("the warning window")
    class Window {

        @Test
        @DisplayName("each item's own alert days decide, so milk and tinned food differ")
        void theItemsOwnWindowIsUsed() {
            ExpiringBatch soon = batch(TODAY.plusDays(10), 14, 5);
            ExpiringBatch same = batch(TODAY.plusDays(10), 3, 5);

            assertTrue(ExpiringItemsReport.isFlagged(soon, TODAY, null));
            assertFalse(ExpiringItemsReport.isFlagged(same, TODAY, null),
                    "the same date is not near for an item that asked for three days' notice");
        }

        @Test
        @DisplayName("the last day of the window is inside it")
        void theWindowBoundaryIsInclusive() {
            assertTrue(ExpiringItemsReport.isFlagged(batch(TODAY.plusDays(14), 14, 5), TODAY, null));
            assertFalse(ExpiringItemsReport.isFlagged(batch(TODAY.plusDays(15), 14, 5), TODAY, null));
        }

        @Test
        @DisplayName("an item with no window set falls back to a month rather than to zero")
        void anUnsetWindowIsNotNoWindow() {
            assertEquals(ExpiringBatch.DEFAULT_ALERT_DAYS, batch(TODAY, 0, 5).effectiveAlertDays());
            assertTrue(ExpiringItemsReport.isFlagged(batch(TODAY.plusDays(20), 0, 5), TODAY, null),
                    "zero alert days must not mean nothing is ever near expiry");
        }

        @Test
        @DisplayName("a horizon overrides every item's window, in both directions")
        void aHorizonReplacesTheWindow() {
            ExpiringBatch far = batch(TODAY.plusDays(60), 7, 5);

            assertFalse(ExpiringItemsReport.isFlagged(far, TODAY, null));
            assertTrue(ExpiringItemsReport.isFlagged(far, TODAY, TODAY.plusDays(90)),
                    "asking about the next ninety days must reach past a seven-day window");
            assertFalse(ExpiringItemsReport.isFlagged(batch(TODAY.plusDays(5), 30, 5),
                            TODAY, TODAY.plusDays(2)),
                    "and asking about the next two days must not widen to a thirty-day one");
        }
    }

    @Nested
    @DisplayName("the report")
    class Result {

        @Test
        @DisplayName("soonest first, so what is already expired heads the page")
        void orderedByDate() {
            ItemReportResult result = ExpiringItemsReport.build(List.of(
                    batch(TODAY.plusDays(5), 30, 1),
                    batch(TODAY.minusDays(3), 30, 1),
                    batch(TODAY.plusDays(1), 30, 1)), TODAY, null);

            List<Object> days = result.rows().stream().map(row -> row.value(7)).toList();
            assertEquals(List.of(-3.0, 1.0, 5.0), days);
        }

        @Test
        @DisplayName("expired value and value still at risk are counted apart - they are different money")
        void thetwoValuesAreSeparate() {
            ItemReportResult result = ExpiringItemsReport.build(List.of(
                    batch(TODAY.minusDays(1), 30, 4),
                    batch(TODAY.plusDays(2), 30, 3)), TODAY, null);

            assertTrue(result.totals().stream().anyMatch(
                    total -> total.labelKey().equals("itemreport.total.expired.value")
                            && total.value().contains("40")));
            assertTrue(result.totals().stream().anyMatch(
                    total -> total.labelKey().equals("itemreport.total.at.risk.value")
                            && total.value().contains("30")));
        }

        @Test
        @DisplayName("a batch far from its date is not in the report at all")
        void healthyStockIsSilent() {
            assertEquals(0, ExpiringItemsReport.build(
                    List.of(batch(TODAY.plusDays(400), 30, 5)), TODAY, null).itemRowCount());
        }
    }
}
