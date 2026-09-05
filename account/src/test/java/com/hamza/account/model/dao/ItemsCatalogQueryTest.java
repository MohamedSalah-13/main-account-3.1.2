package com.hamza.account.model.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the statement the items list is built from.
 * <p>
 * The catalog page and its count are assembled from the same {@code WHERE}, so the two
 * cannot disagree about which rows exist - a page showing rows the count says are not
 * there is a pagination control that lies. And the parameters are assembled beside the
 * SQL that consumes them, which is the arrangement this codebase pins everywhere it
 * occurs: a placeholder without its value is a {@code SQLException}, and a value bound to
 * the wrong placeholder is a search that silently answers a different question.
 */
class ItemsCatalogQueryTest {

    @Test
    @DisplayName("catalog rows omit the picture blob")
    void catalogProjectionDoesNotLoadPictures() {
        String sql = ItemsDao.catalogSelectQuery().toLowerCase();

        assertFalse(sql.contains("select *"));
        assertFalse(sql.contains("item_image"));
    }

    @Test
    @DisplayName("catalog rows read the opening balance aggregated across stocks")
    void catalogProjectionUsesAllStockOpeningBalance() {
        String sql = ItemsDao.catalogSelectQuery();

        assertTrue(sql.contains("SUM(first_balance)"));
        assertTrue(sql.contains("ip.stock_first_balance"));
        assertFalse(sql.contains("items.first_balance"));
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    @Nested
    @DisplayName("every placeholder is bound, and only bound placeholders exist")
    class ParameterCounts {

        @Test
        void plainListing() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("", null, null);

            assertEquals("", query.where());
            assertEquals(0, query.whereParameters().size());
            assertEquals(0, query.orderParameters().size());
            assertEquals("items.id DESC", query.order());
        }

        @Test
        void textSearch() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", null, null);

            assertEquals(placeholders(query.where()), query.whereParameters().size());
            assertEquals(placeholders(query.order()), query.orderParameters().size());
        }

        @Test
        void numericSearch() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("6221", null, null);

            assertEquals(placeholders(query.where()), query.whereParameters().size());
            assertEquals(placeholders(query.order()), query.orderParameters().size());
        }

        @Test
        void searchInsideAGroup() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", 3, 7);

            assertEquals(placeholders(query.where()), query.whereParameters().size());
            assertEquals(placeholders(query.order()), query.orderParameters().size());
        }

        @Test
        void groupWithoutASearch() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("  ", 3, null);

            assertEquals(placeholders(query.where()), query.whereParameters().size());
            assertEquals(0, query.orderParameters().size());
        }
    }

    @Nested
    @DisplayName("what the filter means")
    class Filtering {

        @Test
        @DisplayName("the group narrows the search rather than the other way round")
        void groupAndSearchAreBothApplied() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", null, 7);

            assertTrue(query.where().contains("items.nameItem LIKE ?"));
            assertTrue(query.where().contains("items.sub_num = ?"));
            assertTrue(query.where().contains(" AND "));
        }

        @Test
        @DisplayName("a sub group beats a main group; it is the narrower of the two")
        void subGroupWins() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("", 3, 7);

            assertTrue(query.where().contains("items.sub_num = ?"));
            assertTrue(query.whereParameters().contains(7));
            assertTrue(!query.whereParameters().contains(3));
        }

        @Test
        @DisplayName("a main group asks for every sub group under it")
        void mainGroupExpands() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("", 3, null);

            assertTrue(query.where().contains("SELECT id FROM sub_group WHERE main_id = ?"));
            assertTrue(query.whereParameters().contains(3));
        }

        @Test
        @DisplayName("a search matches a name, and the codes in all three tables")
        void searchesEveryKindOfCode() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", null, null);

            assertTrue(query.where().contains("items.nameItem LIKE ?"));
            assertTrue(query.where().contains("items.barcode LIKE ?"));
            assertTrue(query.where().contains("FROM item_barcodes"));
            assertTrue(query.where().contains("FROM items_units"));
        }

        @Test
        @DisplayName("text is matched as a fragment so a middle word finds its item")
        void textIsMatchedAnywhere() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", null, null);

            assertTrue(query.whereParameters().stream().allMatch(value -> "%لبن%".equals(value)));
        }

        @Test
        @DisplayName("digits alone are an id or a barcode, never a fragment of a name")
        void digitsAreMatchedExactly() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("6221", null, null);

            assertTrue(!query.where().contains("nameItem"));
            assertTrue(!query.where().contains("LIKE"));
            assertEquals(6221, query.whereParameters().getFirst());
        }

        @Test
        @DisplayName("a barcode too long to be an id is still searched as a barcode")
        void oversizedNumberIsNotAnId() {
            String barcode = "62211234567890123456";
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery(barcode, null, null);

            assertEquals(-1, query.whereParameters().getFirst());
            assertTrue(query.whereParameters().contains(barcode));
        }
    }

    @Nested
    @DisplayName("ranking")
    class Ordering {

        @Test
        @DisplayName("an exact code outranks a name that starts with the text, which outranks one that merely contains it")
        void exactBeatsStartsBeatsContains() {
            ItemsDao.CatalogQuery query = ItemsDao.catalogQuery("لبن", null, null);

            int exactBarcode = query.order().indexOf("items.barcode = ?");
            int nameStarts = query.order().indexOf("items.nameItem LIKE ?");
            assertTrue(exactBarcode >= 0 && nameStarts > exactBarcode);
            assertTrue(query.orderParameters().contains("لبن"));
            assertTrue(query.orderParameters().contains("لبن%"));
        }

        @Test
        @DisplayName("the id breaks every tie, so a page boundary falls in the same place twice")
        void orderingIsTotal() {
            assertTrue(ItemsDao.catalogQuery("لبن", null, null).order().endsWith("items.id DESC"));
            assertTrue(ItemsDao.catalogQuery("6221", null, null).order().endsWith("items.id DESC"));
            assertTrue(ItemsDao.catalogQuery("", null, null).order().endsWith("items.id DESC"));
        }
    }
}
