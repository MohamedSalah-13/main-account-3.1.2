package com.hamza.account.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which settings are the shop's and which are the machine's.
 * <p>
 * The list is one line per key and adding to it is easy, which is exactly why it needs a
 * test: sharing a key that is genuinely per-machine is not a compile error and not a
 * crash. It is one wall-mounted price screen reporting another branch's stock, or every
 * till printing to the manager's printer, discovered by a customer.
 */
class SharedSettingKeysTest {

    @Test
    @DisplayName("the scale's barcode layout is the shop's - one sticker, every till")
    void scaleBarcodeIsShared() {
        assertTrue(SharedSettingKeys.isShared("setting.barcode.scale.active"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.start"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.length"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.count.scale"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.count.item"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.has.check.digit"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.validate.check.digit"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.value.type"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.max.weight"));
        assertTrue(SharedSettingKeys.isShared("setting.barcode.min.weight"));
    }

    @Test
    @DisplayName("what a sale or a return is allowed to be is a business rule, not a preference")
    void tradingRulesAreShared() {
        assertTrue(SharedSettingKeys.isShared("return.require.source.invoice"));
        assertTrue(SharedSettingKeys.isShared("return.free.limit"));
        assertTrue(SharedSettingKeys.isShared("item.sel.without.balance"));
        assertTrue(SharedSettingKeys.isShared("invoice.update.price"));
    }

    /**
     * The price-check screen is a display hanging on a wall, and
     * {@code price.check.stock} is the warehouse <em>that</em> display answers for.
     * Sharing it would point every screen in the business at one branch's shelves.
     */
    @Test
    @DisplayName("a fact about one machine stays on that machine")
    void perMachineSettingsAreNotShared() {
        assertFalse(SharedSettingKeys.isShared("price.check.stock"));
        assertFalse(SharedSettingKeys.isShared("price.check.price.tier"));
        assertFalse(SharedSettingKeys.isShared("setting.printer.thermal"));
        assertFalse(SharedSettingKeys.isShared("setting.printer.barcode"));
        assertFalse(SharedSettingKeys.isShared("backup.database.save.folder"));
        assertFalse(SharedSettingKeys.isShared("setting.path.image.main.screen"));
        assertFalse(SharedSettingKeys.isShared("pane.index"));
    }

    @Test
    @DisplayName("the shape of a stored invoice is the shop's, not the till's")
    void invoiceShapeIsShared() {
        // One line of two or two lines of one: the same sale must not be recorded two
        // ways depending on which till served it, because every later reading of those
        // rows - a report, a return against the invoice - then meets both shapes.
        assertTrue(SharedSettingKeys.isShared("invoice.increase.item.one.table"));
    }

    /**
     * The pair that looks symmetrical and is not, which is the whole reason this test
     * names them together. Both are "the default on a new invoice"; only one of them is
     * a fact about the business.
     */
    @Test
    @DisplayName("the default customer is shared, the default delegate is not")
    void theDefaultCustomerIsSharedAndTheDelegateIsNot() {
        // A cash sale filed under a different walk-in account depending on which till
        // served it is a hole in the books.
        assertTrue(SharedSettingKeys.isShared("setting.save.name.customer"));

        // A delegate is a salesperson, and salespeople are paid on what they sell. A till
        // manned by its own salesman must default to that one; sharing this would credit
        // every sale in the building to whoever was set last. Setting it per machine is a
        // chore, paying the wrong person is not.
        assertFalse(SharedSettingKeys.isShared("setting.save.name.delegate"));
    }

    /**
     * Read the call sites before moving a key, because these three read as obvious
     * candidates and each turns out not to be.
     */
    @Test
    @DisplayName("what looks shareable and is not")
    void thingsThatLookSharedButAreNot() {
        // The label group is calibrated to the sticker roll loaded in one machine's label
        // printer - and setting.printer.barcode, its neighbour, is per machine. Sharing
        // the content while the size stays local would split a calibrated group across
        // two stores, which is worse than either whole.
        assertFalse(SharedSettingKeys.isShared("barcode.label.width.mm"));
        assertFalse(SharedSettingKeys.isShared("barcode.label.name.font.size"));
        assertFalse(SharedSettingKeys.isShared("barcode.label.print.price"));

        // A notification rule. Every notification setting in this program is per machine
        // by design - NotificationPreferences persists nothing to the database - and
        // sharing one of them alone would make that family inconsistent.
        assertFalse(SharedSettingKeys.isShared("item.show.alert"));

        // How this operator's till behaves while entering a sale, not what the sale is.
        assertFalse(SharedSettingKeys.isShared("invoice.show.screen.paid"));
        assertFalse(SharedSettingKeys.isShared("invoice.add.items.direct"));
    }

    @Test
    @DisplayName("an unknown key is nobody's")
    void unknownKeysAreLocal() {
        assertFalse(SharedSettingKeys.isShared("something.that.does.not.exist"));
    }
}
