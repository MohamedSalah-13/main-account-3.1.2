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
 * The picture is written only when asked for. The opening balance is not on the item row at all
 * since V78 - {@code ItemsDao.updateBulk} writes it to the default warehouse's {@code items_stock}
 * row, for the items the service decided may take it.
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
        assertFalse(dao.bulkUpdateSql(false).contains("item_image"));
        assertTrue(dao.bulkUpdateSql(true).contains("item_image"));
    }

    @Test
    @DisplayName("the item row carries no opening balance to write")
    void theItemRowHasNoOpeningBalance() {
        assertFalse(dao.bulkUpdateSql(true).contains("first_balance"));
        assertFalse(dao.bulkUpdateSql(false).contains("first_balance"));
    }

    @Test
    @DisplayName("nothing the bulk editor cannot change is written back from the list row")
    void onlyTheEditorsColumnsAreWritten() {
        String sql = dao.bulkUpdateSql(true);

        assertTrue(sql.contains("mini_quantity=?"), sql);
        for (String untouched : new String[]{"barcode", "nameItem", "sel_price2", "sel_price3",
                "unit_id", "item_has_validity"}) {
            assertFalse(sql.contains(untouched), untouched + " is written by the bulk update: " + sql);
        }
    }

    @Test
    @DisplayName("every placeholder has its value, with and without the picture")
    void everyPlaceholderIsBound() {
        for (boolean writesImage : new boolean[]{false, true}) {
            // The optimistic version is appended by optimisticValues, after these.
            assertEquals(placeholders(dao.bulkUpdateSql(writesImage)) - 1,
                    dao.bulkUpdateValues(row(), writesImage).length, "image " + writesImage);
        }
    }

    @Test
    @DisplayName("each value is bound where its column is named")
    void eachValueLandsInItsOwnColumn() {
        Object[] plain = dao.bulkUpdateValues(row(), false);
        assertEquals(7, plain[0]);
        assertEquals(2.0, plain[4]);
        assertEquals(3, plain[5]);
        assertEquals(42, plain[6]);
    }
}
