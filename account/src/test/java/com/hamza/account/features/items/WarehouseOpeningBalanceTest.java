package com.hamza.account.features.items;

import com.hamza.account.opening.OpeningBalanceGuard.Verdict;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An item's opening balance in one warehouse, and when it closes - {@link WarehouseOpeningBalance}
 * apart from the database.
 */
class WarehouseOpeningBalanceTest {

    private static final int ITEM = 5;
    private static final int BRANCH = 2;
    private static final Path BUNDLE_DIR = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");

    /** A reader for a pair that has moved as described and holds {@code stored}. */
    private static WarehouseOpeningBalance over(double stored, Map<String, Integer> counts) {
        return new WarehouseOpeningBalance(new WarehouseOpeningBalance.Reader() {
            @Override
            public Map<String, Integer> openingMovementCounts(int itemId, int stockId) {
                return counts;
            }

            @Override
            public double openingBalance(int itemId, int stockId) {
                return stored;
            }
        });
    }

    @Nested
    @DisplayName("the rule")
    class TheRule {

        @Test
        @DisplayName("an item nothing has moved in the warehouse is open, whatever the new value")
        void unmovedIsOpen() throws DaoException {
            WarehouseOpeningBalance rule = over(100, Map.of());

            assertFalse(rule.isLocked(ITEM, BRANCH));
            assertTrue(rule.mayWrite(ITEM, BRANCH, 999));
            assertEquals(Verdict.OPEN, rule.verdict(ITEM, BRANCH, 999));
        }

        @Test
        @DisplayName("a line in the warehouse closes it")
        void movedIsLocked() throws DaoException {
            assertTrue(over(100, Map.of("quantitySales", 1)).isLocked(ITEM, BRANCH));
        }

        @Test
        @DisplayName("an unchanged value passes and is not written; a changed one is refused")
        void theVerdictAgreesWithMayWrite() throws DaoException {
            WarehouseOpeningBalance rule = over(250, Map.of("quantityPurchase", 3));

            assertEquals(Verdict.UNCHANGED, rule.verdict(ITEM, BRANCH, 250.0001));
            assertFalse(rule.mayWrite(ITEM, BRANCH, 250.0001),
                    "an unchanged opening must still be left out of the write");
            assertEquals(Verdict.REFUSED, rule.verdict(ITEM, BRANCH, 249.99));
            assertThrows(BusinessRuleException.class, () -> rule.mayWrite(ITEM, BRANCH, 249.99));
        }

        @ParameterizedTest(name = "item id {0} is never queried")
        @ValueSource(ints = {0, -1})
        @DisplayName("an item not saved yet is open without asking the database")
        void unsavedIsOpenWithoutAQuery(int itemId) throws DaoException {
            WarehouseOpeningBalance rule = new WarehouseOpeningBalance(new WarehouseOpeningBalance.Reader() {
                @Override
                public Map<String, Integer> openingMovementCounts(int item, int stock) {
                    throw new AssertionError("the database was asked about an item that does not exist yet");
                }

                @Override
                public double openingBalance(int item, int stock) {
                    throw new AssertionError("the balance was read for an item that does not exist yet");
                }
            });

            assertFalse(rule.isLocked(itemId, BRANCH));
            assertTrue(rule.mayWrite(itemId, BRANCH, 7));
        }

        /** The two halves of a transfer are two terms of the balance and one kind of line to a reader. */
        @Test
        @DisplayName("the refusal counts both halves of a transfer as one kind of line")
        void transfersAreOneKindOfLine() throws DaoException {
            Map<String, Integer> counts = new HashMap<>();
            counts.put("fromStock", 2);
            counts.put("toStock", 3);
            counts.put("adjustment", 1);

            var movements = over(0, counts).movements(ITEM, BRANCH);

            assertEquals(2, movements.size(), movements.toString());
            assertEquals("delete.ref.stock_transfer_line", movements.getFirst().labelKey());
            assertEquals(5, movements.getFirst().count());
            assertEquals("opening.ref.stock_count_line", movements.get(1).labelKey());
        }
    }

    @Nested
    @DisplayName("what moves an item")
    class WhatMovesAnItem {

        /**
         * The list is the balance's own ({@code ItemStockBalanceSql.MOVEMENTS}, held to the view), so
         * a term added there reaches this rule by itself - provided it has words for a refusal.
         */
        @Test
        @DisplayName("every term of the balance locks the opening, and has words in all three bundles")
        void everyTermHasALabel() throws Exception {
            List<String> missing = new ArrayList<>();
            for (String bundle : List.of("messages.properties", "messages_ar.properties", "messages_en.properties")) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(BUNDLE_DIR.resolve(bundle), StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                for (ItemStockBalanceSql.Movement movement : ItemStockBalanceSql.MOVEMENTS) {
                    String key = WarehouseOpeningBalance.labelKey(movement);
                    if (!properties.containsKey(key)) {
                        missing.add(bundle + ": " + key);
                    }
                }
                for (String key : List.of("opening.error.locked.warehouse", "opening.correction.items",
                        "opening.movements.separator")) {
                    if (!properties.containsKey(key)) {
                        missing.add(bundle + ": " + key);
                    }
                }
            }
            assertTrue(missing.isEmpty(), String.join("\n", missing));
        }

        @Test
        @DisplayName("both invoice sides, both returns, both halves of a transfer and a count")
        void theSixSourcesAreAllThere() {
            String counts = WarehouseOpeningBalance.MOVEMENT_COUNTS;
            for (String source : List.of("purchase_names_table", "sales_names_table",
                    "purchase_return_names_table", "sales_return_names_table", "stock_transfer_view",
                    "stock_count_lines")) {
                assertTrue(counts.contains(source), source + " is missing from " + counts);
            }
            assertTrue(counts.contains("m.stock_from = ?") && counts.contains("m.stock_to = ?"),
                    "a transfer moves an item in the warehouse it left and the one it reached");
        }

        /**
         * A draft holds the book balance the counter was shown; moving the opening under it makes the
         * sheet post a difference nobody measured. So the view's {@code POSTED} condition is left out.
         */
        @Test
        @DisplayName("a draft count locks the opening as much as a posted one")
        void draftsCount() {
            assertFalse(WarehouseOpeningBalance.MOVEMENT_COUNTS.contains("POSTED"));
            assertFalse(WarehouseOpeningBalance.MOVED.contains("POSTED"));
        }

        @Test
        @DisplayName("the count binds a warehouse and an item per term; the screen's condition binds nothing")
        void theBindingsAreWhatTheDaoSupplies() {
            long marks = WarehouseOpeningBalance.MOVEMENT_COUNTS.chars().filter(c -> c == '?').count();
            assertEquals(2L * ItemStockBalanceSql.MOVEMENTS.size(), marks);
            assertFalse(WarehouseOpeningBalance.MOVED.contains("?"));
            assertEquals(ItemStockBalanceSql.MOVEMENTS.size(),
                    WarehouseOpeningBalance.MOVED.split("EXISTS").length - 1);
        }

        /**
         * An unqualified column the source lacks would resolve to {@code ist}'s own and match every row
         * - {@code ItemStockBalanceSql.correlated}'s warning, and the same qualification here.
         */
        @Test
        @DisplayName("the screen's condition qualifies every column it correlates")
        void theConditionIsQualified() {
            assertTrue(WarehouseOpeningBalance.MOVED.contains("m.num = ist.item_id"));
            assertTrue(WarehouseOpeningBalance.MOVED.contains("sc.stock_id = ist.stock_id AND scl.item_id = ist.item_id"));
            assertFalse(WarehouseOpeningBalance.MOVED.contains("WHERE stock_id"));
        }
    }
}
