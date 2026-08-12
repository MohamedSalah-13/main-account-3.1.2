package com.hamza.account.opening;

import com.hamza.account.delete.Reference;
import com.hamza.account.delete.ReferenceCheck;
import com.hamza.account.delete.ReferenceScanner;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The opening-balance rules, and the value-array surgery that applying them needs.
 * <p>
 * No database: the scanner is substituted, the same way {@code DeletionService} takes
 * its scanner so the delete rules can be exercised without one.
 */
class OpeningBalanceGuardTest {

    /** A scanner that answers with whatever the test says has moved. */
    private static ReferenceScanner scannerReturning(Reference... found) {
        return new ReferenceScanner() {
            @Override
            public List<Reference> scan(List<ReferenceCheck> checks, int id) {
                return List.of(found);
            }
        };
    }

    /** A scanner that fails the test if it is asked anything at all. */
    private static ReferenceScanner scannerThatMustNotRun() {
        return new ReferenceScanner() {
            @Override
            public List<Reference> scan(List<ReferenceCheck> checks, int id) {
                throw new AssertionError("the database was queried for a row that does not exist yet");
            }
        };
    }

    /** A guard over a row that has moved as described, and currently holds {@code stored}. */
    private static OpeningBalanceGuard guardOver(double stored, Reference... movements) {
        return new OpeningBalanceGuard(scannerReturning(movements), (rule, id) -> stored);
    }

    @Nested
    @DisplayName("Locking")
    class Locking {

        @Test
        @DisplayName("a row nothing points at is open, whatever the new value is")
        void unmovedRowIsOpen() throws DaoException {
            OpeningBalanceGuard guard = guardOver(100);

            assertFalse(guard.isLocked(OpeningBalanceRegistry.ITEMS, 5));
            assertTrue(guard.mayWrite(OpeningBalanceRegistry.ITEMS, 5, 999));
        }

        @Test
        @DisplayName("a row with movements is locked")
        void movedRowIsLocked() throws DaoException {
            OpeningBalanceGuard guard = guardOver(100, new Reference("فاتورة بيع", 12));

            assertTrue(guard.isLocked(OpeningBalanceRegistry.CUSTOMERS, 5));
        }

        @ParameterizedTest(name = "id {0} is never queried")
        @ValueSource(ints = {0, -1})
        @DisplayName("a row that has not been saved yet is open without asking the database")
        void unsavedRowIsOpenWithoutAQuery(int id) throws DaoException {
            OpeningBalanceGuard guard = new OpeningBalanceGuard(scannerThatMustNotRun(), (rule, rowId) -> {
                throw new AssertionError("the balance was read for a row that does not exist yet");
            });

            assertFalse(guard.isLocked(OpeningBalanceRegistry.SUPPLIERS, id));
            assertTrue(guard.mayWrite(OpeningBalanceRegistry.SUPPLIERS, id, 500));
        }

        @Test
        @DisplayName("changing the balance of a moved row is refused")
        void changingAMovedRowIsRefused() {
            OpeningBalanceGuard guard = guardOver(250, new Reference("فاتورة بيع", 12));

            assertThrows(DaoException.class,
                    () -> guard.mayWrite(OpeningBalanceRegistry.CUSTOMERS, 9, 500));
        }

        /**
         * The case that would otherwise turn every save into an error: editing a
         * customer's phone number leaves the balance field holding the value it was
         * loaded with, and that must go through.
         */
        @Test
        @DisplayName("saving a moved row without touching the balance is allowed, but does not write it")
        void unchangedBalancePassesWithoutBeingWritten() throws DaoException {
            OpeningBalanceGuard guard = guardOver(250, new Reference("فاتورة بيع", 12));

            assertFalse(guard.mayWrite(OpeningBalanceRegistry.CUSTOMERS, 9, 250),
                    "an unchanged balance must still be left out of the statement");
        }

        @ParameterizedTest(name = "stored 250 vs incoming {0}")
        @ValueSource(doubles = {250.0, 250.0001, 249.9999})
        @DisplayName("a difference below what the column can store is not a change")
        void toleratesRoundingBelowTheColumnsPrecision(double incoming) throws DaoException {
            OpeningBalanceGuard guard = guardOver(250, new Reference("فاتورة بيع", 1));

            assertFalse(guard.mayWrite(OpeningBalanceRegistry.CUSTOMERS, 9, incoming));
        }

        @ParameterizedTest(name = "stored 250 vs incoming {0}")
        @ValueSource(doubles = {250.01, 249.99, 0, -250})
        @DisplayName("a difference the column can store is a change, and is refused")
        void refusesAnythingTheColumnCouldHold(double incoming) {
            OpeningBalanceGuard guard = guardOver(250, new Reference("فاتورة بيع", 1));

            assertThrows(DaoException.class,
                    () -> guard.mayWrite(OpeningBalanceRegistry.CUSTOMERS, 9, incoming));
        }

        @Test
        @DisplayName("the refusal names what moved the row, how many, and what to do instead")
        void theRefusalIsAnswerable() {
            OpeningBalanceGuard guard = guardOver(250,
                    new Reference("فاتورة بيع", 12), new Reference("حركة حساب", 3));

            DaoException refusal = assertThrows(DaoException.class,
                    () -> guard.mayWrite(OpeningBalanceRegistry.CUSTOMERS, 9, 500));

            String message = String.valueOf(refusal.getMessage());
            assertTrue(message.contains("12 فاتورة بيع"), message);
            assertTrue(message.contains("3 حركة حساب"), message);
            assertTrue(message.contains("250"), "the stored balance belongs in the message: " + message);
            assertTrue(message.contains(OpeningBalanceRegistry.CUSTOMERS.correction()), message);
        }
    }

    @Nested
    @DisplayName("Registry")
    class Registry {

        @Test
        @DisplayName("every rule declares what moves it and how to correct it")
        void everyRuleIsComplete() {
            for (OpeningBalanceRule rule : List.of(OpeningBalanceRegistry.ITEMS,
                    OpeningBalanceRegistry.CUSTOMERS, OpeningBalanceRegistry.SUPPLIERS)) {
                assertFalse(rule.entity().isBlank(), "a rule has no entity name");
                assertFalse(rule.table().isBlank(), () -> rule.entity() + " has no table");
                assertFalse(rule.movements().isEmpty(), () -> rule.entity() + " declares no movements");
                // "you cannot" without "do this instead" is where people reach for the
                // database directly.
                assertFalse(rule.correction().isBlank(), () -> rule.entity() + " says nothing to do instead");
            }
        }

        @Test
        @DisplayName("no rule names the same movement table twice")
        void movementsAreDistinct() {
            for (OpeningBalanceRule rule : List.of(OpeningBalanceRegistry.ITEMS,
                    OpeningBalanceRegistry.CUSTOMERS, OpeningBalanceRegistry.SUPPLIERS)) {
                Set<String> seen = new HashSet<>();
                for (ReferenceCheck check : rule.movements()) {
                    assertTrue(seen.add(check.table() + "." + check.column()),
                            () -> rule.entity() + " counts " + check.table() + " twice");
                }
            }
        }

        @Test
        @DisplayName("the item rule covers both invoice sides, both returns, transfers and counts")
        void theItemRuleCoversEveryMovement() {
            List<String> tables = OpeningBalanceRegistry.ITEMS.movements().stream()
                    .map(ReferenceCheck::table).toList();

            assertTrue(tables.containsAll(List.of("purchase", "sales", "purchase_re", "sales_re",
                    "stock_transfer_list", "stock_count_lines")), tables.toString());
        }

        @Test
        @DisplayName("a rule with nothing to check is refused - it would call every row open forever")
        void aRuleNeedsMovements() {
            assertThrows(IllegalArgumentException.class,
                    () -> new OpeningBalanceRule("الصنف", "items", "افعل كذا", List.of()));
        }

        @Test
        @DisplayName("a table name that is not an identifier is refused before it reaches SQL")
        void tableNamesAreIdentifiers() {
            assertThrows(IllegalArgumentException.class, () -> OpeningBalanceRule
                    .forEntity("x", "items; DROP TABLE items")
                    .movedBy("sales", "num", "بيع")
                    .build());
        }
    }

    /**
     * Update parameters are positional, so dropping the opening balance from a column
     * list means dropping it from the values too. An off-by-one here is silent: the area
     * id goes into the user column, or - worse - the id that is the {@code WHERE} clause
     * slips and the wrong row is written.
     */
    @Nested
    @DisplayName("Removing a value")
    class RemovingAValue {

        /** A customer's values, in the order {@code CustomerDao.getData} builds them. */
        private Object[] customerValues() {
            return new Object[]{"محمد", "0100", "القاهرة", "", 5000.0,
                    /* opening balance */ 250.0, 1, 3, 42};
        }

        @Test
        @DisplayName("drops exactly that value and shifts the rest down one")
        void dropsOnlyThatValue() {
            Object[] kept = OpeningBalanceGuard.without(customerValues(), 5);

            assertEquals(8, kept.length);
            assertEquals(5000.0, kept[4]);
            assertEquals(1, kept[5]);
            assertEquals(3, kept[6]);
        }

        @Test
        @DisplayName("the id stays last, because it is the WHERE clause")
        void theIdStaysLast() {
            assertEquals(42, OpeningBalanceGuard.without(customerValues(), 5)[7]);
            // and for the supplier layout, where the balance is one place earlier
            Object[] supplier = {"شركة", "0100", "طنطا", "", 250.0, 3, 42};
            assertEquals(42, OpeningBalanceGuard.without(supplier, 4)[5]);
        }

        @ParameterizedTest(name = "removing index {0}")
        @ValueSource(ints = {0, 1, 5, 8})
        void everyOtherValueSurvives(int index) {
            Object[] all = customerValues();

            Object[] kept = OpeningBalanceGuard.without(all, index);

            Object[] expected = new Object[all.length - 1];
            int at = 0;
            for (int i = 0; i < all.length; i++) {
                if (i != index) {
                    expected[at++] = all[i];
                }
            }
            assertArrayEquals(expected, kept);
        }

        @ParameterizedTest(name = "index {0} is refused")
        @ValueSource(ints = {-1, 9, 99})
        void refusesAnIndexThatIsNotThere(int index) {
            assertThrows(IllegalArgumentException.class,
                    () -> OpeningBalanceGuard.without(customerValues(), index));
        }
    }
}
