package com.hamza.account.features.stockcount;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which of the count screen's controls a reader may use - {@link StockCountControls}. */
class StockCountControlsTest {

    private static Predicate<PermissionKey> holding(PermissionKey... keys) {
        Set<PermissionKey> held = Set.of(keys);
        return held::contains;
    }

    /** The case seen on screen: soha could open the count and was offered Save and Post. */
    @Test
    @DisplayName("a reader who may only look is offered nothing that writes")
    void aReaderIsOfferedNothingThatWrites() {
        assertEquals(new StockCountControls(false, false, false, false),
                StockCountControls.of(true, true, false, holding(AppPermissions.STOCK_COUNT_SHOW)));
    }

    @Test
    @DisplayName("creating a sheet is scanning, saving and discarding it - not posting it")
    void createIsNotPost() {
        assertEquals(new StockCountControls(true, true, false, true),
                StockCountControls.of(true, true, false, holding(AppPermissions.STOCK_COUNT_CREATE)));
    }

    @Test
    @DisplayName("posting alone posts, and builds nothing")
    void postAlonePosts() {
        assertEquals(new StockCountControls(false, false, true, false),
                StockCountControls.of(true, true, false, holding(AppPermissions.STOCK_COUNT_POST)));
    }

    @Test
    @DisplayName("a sheet not saved yet has nothing to discard")
    void aNewSheetHasNothingToDiscard() {
        assertEquals(new StockCountControls(true, true, true, false), StockCountControls.of(true, false, false,
                holding(AppPermissions.STOCK_COUNT_CREATE, AppPermissions.STOCK_COUNT_POST)));
    }

    @Test
    @DisplayName("while a save or a post runs, only the scan box stays open")
    void busyHoldsTheButtons() {
        assertEquals(new StockCountControls(true, false, false, false), StockCountControls.of(true, true, true,
                holding(AppPermissions.STOCK_COUNT_CREATE, AppPermissions.STOCK_COUNT_POST)));
    }

    @Test
    @DisplayName("a posted sheet is read-only whatever the reader holds")
    void aPostedSheetIsReadOnly() {
        assertEquals(new StockCountControls(false, false, false, false), StockCountControls.of(false, true, false,
                holding(AppPermissions.STOCK_COUNT_CREATE, AppPermissions.STOCK_COUNT_POST)));
    }
}
