package com.hamza.account.features.expense;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The value types of the expenses package: an entry, a filter, a summary, a batch, a shortfall. */
class ExpenseValuesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 17);

    @Nested
    class Entry {

        @Test
        @DisplayName("the amount is rounded HALF_UP to the column's two places, once")
        void rounding() throws Exception {
            ExpenseEntry entry = ExpenseEntry.parse(0, DAY, 2, 1, new BigDecimal("10.005"), null, null, null);
            assertEquals(new BigDecimal("10.01"), entry.amount());
        }

        @Test
        @DisplayName("blank text is absent, so '' and NULL cannot search apart")
        void blankIsNull() throws Exception {
            ExpenseEntry entry = ExpenseEntry.parse(0, DAY, 2, 1, BigDecimal.TEN, "  ", "", " ملاحظة ");
            assertNull(entry.payee());
            assertNull(entry.referenceNo());
            assertEquals("ملاحظة", entry.notes());
        }

        @Test
        @DisplayName("each missing or oversized field is refused with its own key")
        void refusals() {
            assertKey("expense.error.date", () -> ExpenseEntry.parse(0, null, 2, 1, BigDecimal.TEN, null, null, null));
            assertKey("expense.error.heading", () -> ExpenseEntry.parse(0, DAY, 0, 1, BigDecimal.TEN, null, null, null));
            assertKey("expenses.error.select.treasury",
                    () -> ExpenseEntry.parse(0, DAY, 2, 0, BigDecimal.TEN, null, null, null));
            assertKey("expense.error.amount", () -> ExpenseEntry.parse(0, DAY, 2, 1, BigDecimal.ZERO, null, null, null));
            assertKey("expense.error.amount.range", () -> ExpenseEntry.parse(0, DAY, 2, 1,
                    new BigDecimal("1000000000000"), null, null, null));
            assertKey("expense.error.payee.length", () -> ExpenseEntry.parse(0, DAY, 2, 1, BigDecimal.TEN,
                    "x".repeat(101), null, null));
            assertKey("expense.error.reference.length", () -> ExpenseEntry.parse(0, DAY, 2, 1, BigDecimal.TEN,
                    null, "x".repeat(51), null));
            assertKey("expense.error.notes.length", () -> ExpenseEntry.parse(0, DAY, 2, 1, BigDecimal.TEN,
                    null, null, "x".repeat(256)));
        }

        private void assertKey(String key, org.junit.jupiter.api.function.Executable action) {
            assertEquals(key, assertThrows(UserValidationException.class, action).getMessage());
        }
    }

    @Nested
    class Filter {

        @Test
        @DisplayName("the period and the text sit in the bar and are not counted on the filters button")
        void panelCount() {
            ExpenseFilter filter = new ExpenseFilter(DAY, DAY, 3, 1, 9, BigDecimal.ZERO, BigDecimal.TEN, "كهرباء",
                    0, 50);
            assertEquals(5, filter.panelConditionCount());
            assertEquals(0, ExpenseFilter.between(DAY, DAY).panelConditionCount());
        }

        @Test
        @DisplayName("an id of zero from an 'all' option is no condition at all")
        void zeroIsAll() {
            ExpenseFilter filter = new ExpenseFilter(null, null, 0, 0, 0, null, null, null, 0, 50);
            assertNull(filter.headingId());
            assertNull(filter.treasuryId());
            assertNull(filter.userId());
        }

        @Test
        @DisplayName("the previous period is the same number of days just before, not the previous calendar month")
        void previousPeriod() {
            ExpenseFilter previous = ExpenseFilter.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 17))
                    .previousPeriod();
            assertEquals(LocalDate.of(2026, 8, 15), previous.from());
            assertEquals(LocalDate.of(2026, 8, 31), previous.to());
            assertNull(ExpenseFilter.between(null, DAY).previousPeriod());
        }

        @Test
        @DisplayName("an impossible set is refused by the record")
        void contradictions() {
            assertThrows(IllegalArgumentException.class, () -> ExpenseFilter.between(DAY, DAY.minusDays(1)));
            assertThrows(IllegalArgumentException.class,
                    () -> new ExpenseFilter(null, null, null, null, null, BigDecimal.TEN, BigDecimal.ONE, "", 0, 50));
        }

        @Test
        @DisplayName("digits are a code; a run too long for one is not")
        void numericText() {
            assertEquals(42, new ExpenseFilter(null, null, null, null, null, null, null, "42", 0, 50).numericText());
            assertEquals(-1, new ExpenseFilter(null, null, null, null, null, null, null, "99999999999", 0, 50)
                    .numericText());
            assertEquals(-1, new ExpenseFilter(null, null, null, null, null, null, null, "إيجار", 0, 50).numericText());
        }
    }

    @Nested
    class Summary {

        @Test
        @DisplayName("an average of nothing is zero, and a change against nothing is absent rather than 100%")
        void emptyAndAbsent() {
            assertEquals(new BigDecimal("0.00"), ExpenseSummary.EMPTY.average());
            assertNull(new ExpenseSummary(1, new BigDecimal("50"), null, null, BigDecimal.ZERO).changePercent());
            assertNull(new ExpenseSummary(1, new BigDecimal("50"), null, null, null).changePercent());
        }

        @Test
        @DisplayName("a fall is a negative change")
        void change() {
            ExpenseSummary summary = new ExpenseSummary(3, new BigDecimal("90.00"), null, null, new BigDecimal("120.00"));
            assertEquals(new BigDecimal("-25.0"), summary.changePercent());
            assertEquals(new BigDecimal("30.00"), summary.average());
        }
    }

    @Nested
    class Shortfall {

        @Test
        @DisplayName("short by what the till does not hold, and zero when it fits")
        void shortfall() {
            assertEquals(new BigDecimal("20.00"), ExpenseBalanceCheck.shortfall(new BigDecimal("80.00"),
                    new BigDecimal("100.00"), BigDecimal.ZERO));
            assertEquals(0, ExpenseBalanceCheck.shortfall(new BigDecimal("80.00"), new BigDecimal("80.00"),
                    null).signum());
        }

        @Test
        @DisplayName("a till already in debt is short by the whole expense and the debt")
        void negativeBalance() {
            assertEquals(new BigDecimal("60.00"), ExpenseBalanceCheck.shortfall(new BigDecimal("-10.00"),
                    new BigDecimal("50.00"), BigDecimal.ZERO));
        }
    }

    @Nested
    class Batch {

        @Test
        @DisplayName("the total, and what each till is asked for together")
        void totals() throws Exception {
            ExpenseBatchDraft draft = new ExpenseBatchDraft();
            draft.add(ExpenseFixtures.entry(2, 1, "400.00"), "كهرباء", "الرئيسية");
            draft.add(ExpenseFixtures.entry(2, 2, "50.00"), "كهرباء", "المحفظة");
            draft.add(ExpenseFixtures.entry(4, 1, "400.00"), "مياه", "الرئيسية");

            assertEquals(new BigDecimal("850.00"), draft.total());
            assertEquals(Map.of(1, new BigDecimal("800.00"), 2, new BigDecimal("50.00")), draft.totalsByTreasury());
        }

        @Test
        @DisplayName("removing a line that is not there removes nothing")
        void removeOutOfRange() throws Exception {
            ExpenseBatchDraft draft = new ExpenseBatchDraft();
            draft.add(ExpenseFixtures.entry(2, 1, "10.00"), "a", "b");
            draft.remove(5);
            draft.remove(-1);
            assertEquals(1, draft.size());
            draft.remove(0);
            assertTrue(draft.isEmpty());
        }

        @Test
        @DisplayName("a batch has a ceiling, and holds only new expenses")
        void ceiling() throws Exception {
            ExpenseBatchDraft draft = new ExpenseBatchDraft();
            for (int i = 0; i < ExpenseBatchDraft.MAX_LINES; i++) {
                draft.add(ExpenseFixtures.entry(2, 1, "1.00"), "a", "b");
            }
            assertEquals("expense.batch.error.too.many", assertThrows(UserValidationException.class,
                    () -> draft.add(ExpenseFixtures.entry(2, 1, "1.00"), "a", "b")).getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> new ExpenseBatchDraft().add(ExpenseFixtures.entry(2, 1, "1.00").withId(7), "a", "b"));
        }
    }
}
