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
 * The picture and the opening balance are each written only when asked for.
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
        item.setFirstBalanceForStock(12.5);
        return item;
    }

    @Test
    @DisplayName("the picture is written only when the bulk editor was asked to change it")
    void thePictureIsWrittenOnlyOnRequest() {
        assertFalse(dao.bulkUpdateSql(false, false).contains("item_image"));
        assertTrue(dao.bulkUpdateSql(true, false).contains("item_image"));
    }

    @Test
    @DisplayName("the opening balance is written only for an item it was decided for")
    void theOpeningBalanceIsWrittenOnlyOnRequest() {
        assertFalse(dao.bulkUpdateSql(true, false).contains("first_balance"));
        assertTrue(dao.bulkUpdateSql(false, true).contains("first_balance=?"));
    }

    @Test
    @DisplayName("nothing the bulk editor cannot change is written back from the list row")
    void onlyTheEditorsColumnsAreWritten() {
        String sql = dao.bulkUpdateSql(true, true);

        assertTrue(sql.contains("mini_quantity=?"), sql);
        for (String untouched : new String[]{"barcode", "nameItem", "sel_price2", "sel_price3",
                "unit_id", "item_has_validity"}) {
            assertFalse(sql.contains(untouched), untouched + " is written by the bulk update: " + sql);
        }
    }

    @Test
    @DisplayName("every placeholder has its value, in every combination")
    void everyPlaceholderIsBound() {
        for (boolean writesImage : new boolean[]{false, true}) {
            for (boolean writesOpening : new boolean[]{false, true}) {
                // The optimistic version is appended by optimisticValues, after these.
                assertEquals(placeholders(dao.bulkUpdateSql(writesImage, writesOpening)) - 1,
                        dao.bulkUpdateValues(row(), writesImage, writesOpening).length,
                        "image " + writesImage + ", opening " + writesOpening);
            }
        }
    }

    @Test
    @DisplayName("each value is bound where its column is named")
    void eachValueLandsInItsOwnColumn() {
        Object[] plain = dao.bulkUpdateValues(row(), false, false);
        assertEquals(7, plain[0]);
        assertEquals(2.0, plain[4]);
        assertEquals(3, plain[5]);
        assertEquals(42, plain[6]);

        Object[] withOpening = dao.bulkUpdateValues(row(), false, true);
        assertEquals(12.5, withOpening[5]);
        assertEquals(3, withOpening[6]);
        assertEquals(42, withOpening[7]);
    }
}
