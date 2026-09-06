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
    @DisplayName("an unknown key is nobody's")
    void unknownKeysAreLocal() {
        assertFalse(SharedSettingKeys.isShared("something.that.does.not.exist"));
    }
}
