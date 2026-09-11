package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.model.domain.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the columns the bulk editor writes.
 * <p>
 * Its rows are the items list's, mapped for display, and a list row carries no picture, no units
 * and no extra barcodes. The statement used to name every column of the item and write each one
 * from the row, so saving a price rise over a batch wrote an empty picture over every item in it.
 */
class ItemsBulkUpdateTest {

    private final ItemsDao dao = new ItemsDao(null);

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    private static ItemsModel row() {
        SubGroups group = new SubGroups();
        group.setId(7);
        Users user = new Users();
        user.setId(3);
        ItemsModel item = new ItemsModel();
        item.setId(42);
        item.setSubGroups(group);
        item.setUsers(user);
        item.setMini_quantity(2);
        return item;
    }

    @Test
    @DisplayName("the picture is written only when the bulk editor was asked to change it")
    void thePictureIsWrittenOnlyOnRequest() {
        assertFalse(dao.bulkUpdateSql(false).contains("item_image"));
        assertTrue(dao.bulkUpdateSql(true).contains("item_image"));
    }

    @Test
    @DisplayName("nothing the bulk editor cannot change is written back from the list row")
    void onlyTheEditorsColumnsAreWritten() {
        String sql = dao.bulkUpdateSql(true);

        assertTrue(sql.contains("mini_quantity=?"), sql);
        for (String untouched : new String[]{"barcode", "nameItem", "sel_price2", "sel_price3",
                "unit_id", "first_balance", "item_has_validity"}) {
            assertFalse(sql.contains(untouched), untouched + " is written by the bulk update: " + sql);
        }
    }

    @Test
    @DisplayName("every placeholder has its value, with and without the picture")
    void everyPlaceholderIsBound() {
        for (boolean writesImage : new boolean[]{false, true}) {
            // The optimistic version is appended by optimisticValues, after these.
            assertEquals(placeholders(dao.bulkUpdateSql(writesImage)) - 1,
                    dao.bulkUpdateValues(row(), writesImage).length);
        }
    }

    @Test
    @DisplayName("the minimum quantity is bound where its column is named")
    void theMinimumLandsInItsOwnColumn() {
        Object[] values = dao.bulkUpdateValues(row(), false);

        assertEquals(7, values[0]);
        assertEquals(2.0, values[4]);
        assertEquals(3, values[5]);
        assertEquals(42, values[6]);
    }
}
