package com.hamza.account.features.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two pieces of the inventory screen that are arithmetic rather than a query, so
 * they can be checked without a database or a JavaFX toolkit.
 * <p>
 * Both had a defect worth pinning down. The page count was {@code (total / size) + 1},
 * which offers an empty last page whenever the items divide exactly; and a new search
 * kept the current page, so searching from page 7 landed on page 7 of two results and
 * showed an empty table that reads as "nothing matched".
 */
class InventoryPagingTest {

    private static InventoryPage pageOf(long itemCount) {
        return new InventoryPage(List.of(),
                new InventorySummary(itemCount, 0, 0, 0, 0, 0, 0));
    }

    @Nested
    @DisplayName("Page count")
    class PageCount {

        @ParameterizedTest(name = "{0} items of {1} per page -> {2} pages")
        @CsvSource({
                // an exact multiple must not add an empty page on the end
                "100, 50, 2",
                "50,  50, 1",
                "150, 50, 3",
                // a partial last page counts
                "101, 50, 3",
                "51,  50, 2",
                "1,   50, 1",
        })
        void countsPages(long items, int pageSize, int expected) {
            assertEquals(expected, pageOf(items).pageCount(pageSize));
        }

        @Test
        @DisplayName("an empty stock still has one page, not zero")
        void emptyStockHasOnePage() {
            assertEquals(1, pageOf(0).pageCount(50));
        }

        @Test
        @DisplayName("the row count is the summary's item count")
        void totalRowsComesFromTheSummary() {
            assertEquals(4321, pageOf(4321).totalRows());
        }
    }

    @Nested
    @DisplayName("Query")
    class Query {

        @Test
        @DisplayName("a new search returns to the first page")
        void searchingResetsThePage() {
            InventoryQuery onPageSeven = InventoryQuery.all().withPage(7);

            InventoryQuery searched = onPageSeven.withSearch("سكر");

            assertEquals(0, searched.page());
            assertEquals("سكر", searched.search());
        }

        @Test
        @DisplayName("turning a page keeps the search")
        void pagingKeepsTheSearch() {
            InventoryQuery searched = InventoryQuery.all().withSearch("سكر");

            InventoryQuery turned = searched.withPage(3);

            assertEquals("سكر", turned.search());
            assertEquals(3, turned.page());
        }

        @ParameterizedTest(name = "[{0}] is not a search")
        @CsvSource(value = {"''", "'   '"})
        void blankIsNoSearch(String text) {
            assertFalse(InventoryQuery.all().withSearch(text).hasSearch());
        }

        @Test
        @DisplayName("a search is trimmed, so a trailing space still matches")
        void searchIsTrimmed() {
            InventoryQuery query = InventoryQuery.all().withSearch("  سكر  ");

            assertTrue(query.hasSearch());
            assertEquals("سكر", query.search());
        }

        @Test
        @DisplayName("null search behaves as no search")
        void nullIsNoSearch() {
            assertFalse(new InventoryQuery(null, StockFilter.ALL, 0, true, 0, 50).hasSearch());
        }

        @Test
        @DisplayName("null level behaves as no narrowing")
        void nullLevelIsAll() {
            assertEquals(StockFilter.ALL, new InventoryQuery("", null, 0, true, 0, 50).level());
        }

        @Test
        @DisplayName("stopped items are counted unless the user asks otherwise")
        void inactiveItemsAreIncludedByDefault() {
            // A stopped item that still holds stock is still stock; leaving it out of
            // the valuation by default would understate what the shop is holding.
            assertTrue(InventoryQuery.all().includeInactive());
        }

        @Test
        @DisplayName("a group narrows, and every narrowing returns to the first page")
        void groupNarrowsAndResetsThePage() {
            InventoryQuery query = InventoryQuery.all().withSearch("سكر").withPage(6);

            InventoryQuery grouped = query.withMainGroup(3);

            assertTrue(grouped.hasGroup());
            assertEquals(3, grouped.mainGroupId());
            assertEquals(0, grouped.page());
            assertEquals("سكر", grouped.search());
        }

        @Test
        @DisplayName("group zero means every group")
        void groupZeroIsAll() {
            assertFalse(InventoryQuery.all().hasGroup());
            assertFalse(InventoryQuery.all().withMainGroup(InventoryQuery.ALL_GROUPS).hasGroup());
            assertFalse(InventoryQuery.all().withMainGroup(-3).hasGroup());
        }

        @Test
        @DisplayName("hiding stopped items returns to the first page and keeps the rest")
        void inactiveTogglesAndResetsThePage() {
            InventoryQuery query = InventoryQuery.all().withMainGroup(2).withPage(5);

            InventoryQuery hidden = query.withInactive(false);

            assertFalse(hidden.includeInactive());
            assertEquals(2, hidden.mainGroupId());
            assertEquals(0, hidden.page());
        }

        @Test
        @DisplayName("a fresh query is not narrowed, and each filter alone narrows it")
        void narrowingIsDetected() {
            assertFalse(InventoryQuery.all().isNarrowed());
            assertTrue(InventoryQuery.all().withSearch("سكر").isNarrowed());
            assertTrue(InventoryQuery.all().withMainGroup(1).isNarrowed());
            assertTrue(InventoryQuery.all().withLevel(StockFilter.LOW).isNarrowed());
            assertTrue(InventoryQuery.all().withInactive(false).isNarrowed());
            // Turning a page is not a filter - the totals still cover everything.
            assertFalse(InventoryQuery.all().withPage(3).isNarrowed());
        }

        @Test
        @DisplayName("every filter survives a page turn")
        void pagingKeepsEveryFilter() {
            InventoryQuery query = InventoryQuery.all()
                    .withSearch("سكر")
                    .withLevel(StockFilter.LOW)
                    .withMainGroup(4)
                    .withInactive(false)
                    .withPage(2);

            assertEquals("سكر", query.search());
            assertEquals(StockFilter.LOW, query.level());
            assertEquals(4, query.mainGroupId());
            assertFalse(query.includeInactive());
            assertEquals(2, query.page());
        }

        @Test
        @DisplayName("narrowing by stock state returns to the first page and keeps the search")
        void levelResetsThePageAndKeepsTheSearch() {
            InventoryQuery query = InventoryQuery.all().withSearch("سكر").withPage(4);

            InventoryQuery narrowed = query.withLevel(StockFilter.LOW);

            assertEquals(0, narrowed.page());
            assertEquals("سكر", narrowed.search());
            assertEquals(StockFilter.LOW, narrowed.level());
        }

        @Test
        @DisplayName("turning a page keeps the stock state")
        void pagingKeepsTheLevel() {
            InventoryQuery query = InventoryQuery.all().withLevel(StockFilter.NEGATIVE);

            assertEquals(StockFilter.NEGATIVE, query.withPage(2).level());
        }

        @Test
        @DisplayName("offset follows the page")
        void offsetFollowsThePage() {
            assertEquals(0, InventoryQuery.all().offset());
            assertEquals(150, InventoryQuery.all().withPage(3).offset());
        }

        @Test
        @DisplayName("a negative page or a zero page size cannot be constructed")
        void guardsAgainstNonsense() {
            InventoryQuery query = new InventoryQuery("", StockFilter.ALL, 0, true, -4, 0);

            assertEquals(0, query.page());
            assertEquals(InventoryQuery.DEFAULT_PAGE_SIZE, query.pageSize());
        }
    }
}
