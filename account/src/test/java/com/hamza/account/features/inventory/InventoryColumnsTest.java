package com.hamza.account.features.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The column catalogue, checked without a JavaFX toolkit - which is the point of
 * describing the columns instead of building them in the controller.
 * <p>
 * The ids matter more than they look: {@code TableSetting} remembers each column's
 * width and visibility against its id, so a duplicate would have two columns sharing
 * one saved width and a blank one would fall back to the position that this catalogue
 * exists to stop mattering.
 */
class InventoryColumnsTest {

    private static final InventoryRow ROW = new InventoryRow(
            7, "سكر", "6221", "كرتونة", true,
            10, 100, 40, 5, 2, 3, 1, -6,
            65, 12.5, 18.0, 20);

    private static List<InventoryColumn> leaves() {
        return InventoryColumns.ALL.stream().flatMap(column -> column.leaves().stream()).toList();
    }

    @Nested
    @DisplayName("Catalogue")
    class Catalogue {

        @Test
        @DisplayName("every column id is unique, headings included")
        void idsAreUnique() {
            Set<String> seen = new HashSet<>();
            for (InventoryColumn column : InventoryColumns.ALL) {
                assertTrue(seen.add(column.id()), () -> "duplicate column id: " + column.id());
                for (InventoryColumn child : column.children()) {
                    assertTrue(seen.add(child.id()), () -> "duplicate column id: " + child.id());
                }
            }
        }

        @Test
        @DisplayName("no column is left without an id or a title")
        void everyColumnIsNamed() {
            for (InventoryColumn column : leaves()) {
                assertFalse(column.id().isBlank(), "a column has no id");
                assertFalse(column.title().isBlank(), () -> column.id() + " has no title");
            }
        }

        @Test
        @DisplayName("every leaf reads a value without failing")
        void everyLeafReads() {
            for (InventoryColumn column : leaves()) {
                assertFalse(column.render(ROW) == null, () -> column.id() + " read nothing");
            }
        }

        @Test
        @DisplayName("a heading has children and reads nothing itself")
        void headingsHaveChildren() {
            assertTrue(InventoryColumns.AT_COST.isGroup());
            assertEquals(2, InventoryColumns.AT_COST.children().size());
            assertEquals("", InventoryColumns.AT_COST.render(ROW));
        }

        @Test
        @DisplayName("price comes before total under a heading, which a HashMap could not promise")
        void headingChildrenAreOrdered() {
            List<InventoryColumn> children = InventoryColumns.AT_COST.children();

            assertEquals("purchaseValuePrice", children.get(0).id());
            assertEquals("purchaseValueTotal", children.get(1).id());
        }
    }

    @Nested
    @DisplayName("Rendering")
    class Rendering {

        @Test
        @DisplayName("quantities carry three decimals, money two")
        void decimalsFollowTheKind() {
            assertEquals("65.000", InventoryColumns.BALANCE.render(ROW));
            assertEquals("12.50", InventoryColumns.AT_COST.children().get(0).render(ROW));
        }

        @Test
        @DisplayName("the value column is the balance at the item's price")
        void valueColumnMatchesTheRow() {
            assertEquals("812.50", InventoryColumns.AT_COST.children().get(1).render(ROW));
            assertEquals("1,170.00", InventoryColumns.AT_SALE.children().get(1).render(ROW));
        }

        @Test
        @DisplayName("figures carry thousands separators and Western digits")
        void thousandsAreSeparated() {
            assertEquals("1,234,567.89", ColumnKind.MONEY.format(1234567.89));
            assertEquals("1,000.500", ColumnKind.QUANTITY.format(1000.5));
        }

        @Test
        @DisplayName("text columns read the item, not a number")
        void textColumnsReadText() {
            assertEquals("سكر", InventoryColumns.NAME.render(ROW));
            assertEquals("كرتونة", InventoryColumns.UNIT.render(ROW));
        }

        @Test
        @DisplayName("only numeric columns are right-aligned")
        void alignmentFollowsTheKind() {
            assertFalse(InventoryColumns.NAME.kind().numeric());
            assertTrue(InventoryColumns.BALANCE.kind().numeric());
            assertTrue(InventoryColumns.AT_COST.children().get(0).kind().numeric());
        }
    }

    @Nested
    @DisplayName("Declaration errors")
    class Declarations {

        @Test
        @DisplayName("a leaf with no reader is refused rather than failing at the first cell")
        void leafNeedsAReader() {
            assertThrows(IllegalArgumentException.class, () -> new InventoryColumn(
                    "broken", "عمود", ColumnKind.MONEY, null, null, 100, List.of()));
        }

        @Test
        @DisplayName("a heading cannot also read a value of its own")
        void headingReadsNothing() {
            assertThrows(IllegalArgumentException.class, () -> new InventoryColumn(
                    "broken", "عمود", ColumnKind.MONEY, null, InventoryRow::balance, 100,
                    List.of(InventoryColumns.BALANCE)));
        }

        @Test
        @DisplayName("a numeric column cannot be read as text")
        void kindAndReaderMustAgree() {
            assertThrows(IllegalArgumentException.class, () -> new InventoryColumn(
                    "broken", "عمود", ColumnKind.QUANTITY, InventoryRow::nameItem, null, 100, List.of()));
        }
    }
}
