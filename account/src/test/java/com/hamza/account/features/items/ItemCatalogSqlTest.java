package com.hamza.account.features.items;

import com.hamza.account.features.items.ItemCatalogFilter.BalanceRule;
import com.hamza.account.features.items.ItemCatalogFilter.MatchMode;
import com.hamza.account.features.items.ItemCatalogFilter.SearchScope;
import com.hamza.account.features.items.ItemCatalogFilter.Tristate;
import com.hamza.account.features.items.ItemCatalogFilter.UsageRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what each filter <em>means</em> in SQL.
 * <p>
 * The two rules worth failing a build over: every placeholder has a value bound to it, and
 * the count knows about every condition the page applies. A value bound to the wrong
 * placeholder is not an exception - it is a list that quietly answers a different question,
 * and a count taken over a narrower {@code FROM} than the page is a pagination control that
 * offers pages the query cannot fill.
 */
class ItemCatalogSqlTest {

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    private static ItemCatalogSql.Statement build(ItemCatalogFilter filter) {
        return ItemCatalogSql.build(filter);
    }

    @Nested
    @DisplayName("every placeholder is bound, whatever the combination")
    class ParameterCounts {

        @Test
        void nothingFilters() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY);

            assertEquals("", statement.where());
            assertEquals(0, statement.whereParameters().size());
            assertEquals(0, statement.orderParameters().size());
        }

        @Test
        @DisplayName("a filter with every condition set at once still binds exactly what it declares")
        void everythingAtOnce() {
            ItemCatalogFilter filter = ItemCatalogFilter.EMPTY
                    .withSearch("لبن")
                    .withGroup(3, 7)
                    .withActive(Tristate.YES)
                    .withHasBarcode(Tristate.NO)
                    .withTracksExpiry(Tristate.YES)
                    .withBalance(BalanceRule.BELOW_MINIMUM)
                    .withUsage(UsageRule.NEVER_SOLD)
                    .withSellPriceBetween(5.0, 50.0);

            ItemCatalogSql.Statement statement = build(filter);

            assertEquals(placeholders(statement.where()), statement.whereParameters().size());
            assertEquals(placeholders(statement.order()), statement.orderParameters().size());
        }

        @Test
        @DisplayName("the conditions that are pure SQL bind nothing at all")
        void flagsBindNoValues() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                    .withActive(Tristate.NO)
                    .withHasBarcode(Tristate.YES)
                    .withBalance(BalanceRule.NEGATIVE)
                    .withUsage(UsageRule.NEVER_MOVED));

            assertEquals(0, statement.whereParameters().size());
            assertEquals(0, placeholders(statement.where()));
        }

        @Test
        void everySearchScopeBindsWhatItDeclares() {
            for (SearchScope scope : SearchScope.values()) {
                ItemCatalogSql.Statement statement =
                        build(ItemCatalogFilter.EMPTY.withSearch("لبن").withSearchScope(scope));

                assertEquals(placeholders(statement.where()), statement.whereParameters().size(),
                        scope + " binds the wrong number of where values");
                assertEquals(placeholders(statement.order()), statement.orderParameters().size(),
                        scope + " binds the wrong number of order values");
            }
        }
    }

    @Nested
    @DisplayName("the count has to know about every condition the page applies")
    class CountAndPageAgree {

        @Test
        @DisplayName("balance filters use the same all-stock opening shown in the row")
        void balanceUsesTheAggregatedOpening() {
            String where = build(ItemCatalogFilter.EMPTY.withBalance(BalanceRule.IN_STOCK)).where();

            assertTrue(where.contains("ip.stock_first_balance"));
            assertFalse(where.contains("items.first_balance"));
        }

        @Test
        @DisplayName("a balance condition names the joined movement row, so the count must join it")
        void balanceRequiresTheJoin() {
            for (BalanceRule rule : BalanceRule.values()) {
                ItemCatalogFilter filter = ItemCatalogFilter.EMPTY.withBalance(rule);
                boolean mentionsJoin = build(filter).where().contains("ip.");

                assertEquals(mentionsJoin, ItemCatalogSql.requiresMovementJoin(filter),
                        rule + " disagrees about whether the count needs the movement join");
            }
        }

        @Test
        @DisplayName("no other condition reaches past items, so no other forces the join")
        void everythingElseStaysOnItems() {
            ItemCatalogFilter filter = ItemCatalogFilter.EMPTY
                    .withSearch("لبن").withGroup(3, null)
                    .withActive(Tristate.YES).withHasBarcode(Tristate.NO)
                    .withTracksExpiry(Tristate.YES).withUsage(UsageRule.NEVER_MOVED)
                    .withSellPriceBetween(1.0, 2.0);

            assertFalse(build(filter).where().contains("ip."));
            assertFalse(ItemCatalogSql.requiresMovementJoin(filter));
        }
    }

    @Nested
    @DisplayName("what each condition means")
    class Meaning {

        @Test
        @DisplayName("a minimum of zero means none is set, so such an item is never below it")
        void aMinimumOfZeroIsNotAMinimum() {
            String where = build(ItemCatalogFilter.EMPTY.withBalance(BalanceRule.BELOW_MINIMUM)).where();

            assertTrue(where.contains("items.mini_quantity > 0"),
                    "an item with no minimum set would otherwise be reported as below it");
        }

        @Test
        @DisplayName("a blank barcode is as codeless as a null one; both occur in this schema")
        void blankBarcodeCountsAsMissing() {
            String where = build(ItemCatalogFilter.EMPTY.withHasBarcode(Tristate.NO)).where();

            assertTrue(where.contains("items.barcode IS NULL"));
            assertTrue(where.contains("items.barcode = ''"));
        }

        @Test
        @DisplayName("never moved means none of the four documents, not sales alone")
        void unusedLooksAtAllFourDocuments() {
            String where = build(ItemCatalogFilter.EMPTY.withUsage(UsageRule.NEVER_MOVED)).where();

            assertTrue(where.contains("FROM sales "), "sales missing");
            assertTrue(where.contains("FROM purchase "), "purchases missing");
            assertTrue(where.contains("FROM sales_re "), "sales returns missing");
            assertTrue(where.contains("FROM purchase_re "), "purchase returns missing");
        }

        @Test
        @DisplayName("never sold is a narrower question than never moved, and asks only about sales")
        void neverSoldIsOnlySales() {
            String where = build(ItemCatalogFilter.EMPTY.withUsage(UsageRule.NEVER_SOLD)).where();

            assertTrue(where.contains("FROM sales"));
            assertFalse(where.contains("FROM purchase"));
        }

        @Test
        @DisplayName("a sub group beats a main group; it is the narrower of the two")
        void subGroupWins() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY.withGroup(3, 7));

            assertTrue(statement.where().contains("items.sub_num = ?"));
            assertTrue(statement.whereParameters().contains(7));
            assertFalse(statement.whereParameters().contains(3));
        }

        @Test
        @DisplayName("digits alone are an id or a barcode, never a fragment of a name")
        void digitsAreMatchedExactly() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY.withSearch("6221"));

            assertFalse(statement.where().contains("nameItem"));
            assertFalse(statement.where().contains("LIKE"));
            assertEquals(6221, statement.whereParameters().getFirst());
        }

        @Test
        @DisplayName("a barcode too long to be an id is still searched as a barcode")
        void oversizedNumberIsNotAnId() {
            String barcode = "62211234567890123456";
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY.withSearch(barcode));

            assertEquals(-1, statement.whereParameters().getFirst());
            assertTrue(statement.whereParameters().contains(barcode));
        }

        @Test
        @DisplayName("a search scoped to the code asks about the id and nothing else")
        void codeScopeIsExact() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                    .withSearch("6221").withSearchScope(SearchScope.CODE));

            assertEquals("WHERE items.id = ?", statement.where().trim());
            assertEquals(6221, statement.whereParameters().getFirst());
        }

        @Test
        @DisplayName("a search scoped to the barcode still looks in all three code tables")
        void barcodeScopeCoversEveryCodeTable() {
            String where = build(ItemCatalogFilter.EMPTY
                    .withSearch("6221").withSearchScope(SearchScope.BARCODE)).where();

            assertTrue(where.contains("items.barcode LIKE ?"));
            assertTrue(where.contains("FROM item_barcodes"));
            assertTrue(where.contains("FROM items_units"));
        }

        @Test
        @DisplayName("the id breaks every tie, so a page boundary falls in the same place twice")
        void orderingIsTotal() {
            assertTrue(build(ItemCatalogFilter.EMPTY.withSearch("لبن")).order().endsWith("items.id DESC"));
            assertTrue(build(ItemCatalogFilter.EMPTY.withSearch("6221")).order().endsWith("items.id DESC"));
            assertTrue(build(ItemCatalogFilter.EMPTY).order().endsWith("items.id DESC"));
        }
    }

    @Nested
    @DisplayName("how the typed text is compared")
    class Matching {

        @Test
        @DisplayName("the smart default is unchanged: digits exact, words as a fragment")
        void autoKeepsTheOldRule() {
            assertFalse(build(ItemCatalogFilter.EMPTY.withSearch("6221")).where().contains("LIKE"));
            assertTrue(build(ItemCatalogFilter.EMPTY.withSearch("لبن")).where().contains("LIKE"));
        }

        @Test
        @DisplayName("an explicit contains searches digits as a fragment - which AUTO refuses to guess")
        void explicitContainsOverridesTheDigitRule() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                    .withSearch("6221").withMatchMode(MatchMode.CONTAINS));

            assertTrue(statement.where().contains("LIKE"));
            assertTrue(statement.whereParameters().stream().allMatch(value -> "%6221%".equals(value)));
        }

        @Test
        void exactComparesWithEqualsAndBindsTheTextAsTyped() {
            ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                    .withSearch("لبن").withMatchMode(MatchMode.EXACT));

            assertTrue(statement.where().contains("items.nameItem = ?"));
            assertFalse(statement.where().contains("LIKE"));
            assertTrue(statement.whereParameters().stream().allMatch(value -> "لبن".equals(value)));
        }

        @Test
        void startsAndEndsAnchorThePatternOnOppositeSides() {
            assertTrue(build(ItemCatalogFilter.EMPTY.withSearch("لبن").withMatchMode(MatchMode.STARTS_WITH))
                    .whereParameters().stream().allMatch(value -> "لبن%".equals(value)));
            assertTrue(build(ItemCatalogFilter.EMPTY.withSearch("لبن").withMatchMode(MatchMode.ENDS_WITH))
                    .whereParameters().stream().allMatch(value -> "%لبن".equals(value)));
        }

        @Test
        @DisplayName("an explicit mode still looks in every table the code could be in")
        void explicitModesCoverEveryCodeTable() {
            String where = build(ItemCatalogFilter.EMPTY
                    .withSearch("622").withMatchMode(MatchMode.STARTS_WITH)).where();

            assertTrue(where.contains("items.nameItem"));
            assertTrue(where.contains("items.barcode"));
            assertTrue(where.contains("FROM item_barcodes"));
            assertTrue(where.contains("FROM items_units"));
        }

        @Test
        @DisplayName("every mode crossed with every scope binds exactly what it declares")
        void everyCombinationBindsWhatItDeclares() {
            for (MatchMode mode : MatchMode.values()) {
                for (SearchScope scope : SearchScope.values()) {
                    ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                            .withSearch("6221").withSearchScope(scope).withMatchMode(mode));

                    assertEquals(placeholders(statement.where()), statement.whereParameters().size(),
                            mode + " / " + scope + " binds the wrong number of where values");
                    assertEquals(placeholders(statement.order()), statement.orderParameters().size(),
                            mode + " / " + scope + " binds the wrong number of order values");
                }
            }
        }

        @Test
        @DisplayName("a code search is an id match whatever the mode says - an id is a number")
        void codeScopeIgnoresTheMode() {
            for (MatchMode mode : MatchMode.values()) {
                ItemCatalogSql.Statement statement = build(ItemCatalogFilter.EMPTY
                        .withSearch("6221").withSearchScope(SearchScope.CODE).withMatchMode(mode));

                assertTrue(statement.where().contains("items.id = ?"), mode.toString());
                assertEquals(6221, statement.whereParameters().getFirst());
            }
        }

        @Test
        @DisplayName("choosing a mode counts as a condition, so the badge shows the list is narrowed")
        void anExplicitModeIsACondition() {
            assertEquals(0, ItemCatalogFilter.EMPTY.activeConditionCount());
            assertEquals(1, ItemCatalogFilter.EMPTY.withMatchMode(MatchMode.EXACT).activeConditionCount());
        }
    }

    @Nested
    @DisplayName("the filter itself")
    class FilterValue {

        @Test
        @DisplayName("blank text is no search, however it was typed")
        void blankTextIsNotASearch() {
            assertTrue(ItemCatalogFilter.EMPTY.withSearch("   ").isEmpty());
            assertTrue(ItemCatalogFilter.EMPTY.withSearch(null).isEmpty());
        }

        @Test
        @DisplayName("the badge counts conditions, so one left on in a collapsed panel is visible")
        void conditionsAreCounted() {
            assertEquals(0, ItemCatalogFilter.EMPTY.activeConditionCount());
            assertEquals(0, ItemCatalogFilter.EMPTY.withSearch("لبن").activeConditionCount(),
                    "the search box is on screen already; counting it would double-report it");
            assertEquals(3, ItemCatalogFilter.EMPTY
                    .withGroup(1, null)
                    .withActive(Tristate.NO)
                    .withBalance(BalanceRule.NEGATIVE)
                    .activeConditionCount());
        }

        @Test
        @DisplayName("a price range is one condition, not two, however many bounds it has")
        void aPriceRangeCountsOnce() {
            assertEquals(1, ItemCatalogFilter.EMPTY.withSellPriceBetween(5.0, 50.0).activeConditionCount());
            assertEquals(1, ItemCatalogFilter.EMPTY.withSellPriceBetween(5.0, null).activeConditionCount());
        }

        @Test
        @DisplayName("typing does not change which conditions are set - that is what a chip keeps")
        void textIsNotACondition() {
            ItemCatalogFilter chip = ItemCatalogFilter.EMPTY.withBalance(BalanceRule.NEGATIVE);

            assertTrue(chip.sameConditionsAs(chip.withSearch("لبن")));
        }
    }
}
