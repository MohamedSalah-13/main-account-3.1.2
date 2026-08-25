package com.hamza.account.features.itemmerge;

import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The two things a merge refuses, and the many it does not.
 * <p>
 * {@link ItemMergeService#checkRules} is a pure function of the two items for exactly
 * this reason: the rules are the part a user meets, and they should not need a database,
 * a signed-in user or a JavaFX toolkit to be held to.
 */
class ItemMergeRulesTest {

    private static final int PIECE = 1;
    private static final int CARTON = 2;

    private static MergeItem item(int id, int unitId, boolean validity) {
        return new MergeItem(id, "صنف " + id, "100" + id, unitId, validity, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("two ordinary items of the same base unit merge")
    void theOrdinaryCase() {
        assertDoesNotThrow(() -> ItemMergeService.checkRules(item(1, PIECE, false), item(2, PIECE, false)));
    }

    @Test
    @DisplayName("an item cannot be merged into itself")
    void notIntoItself() {
        assertThrows(BusinessRuleException.class,
                () -> ItemMergeService.checkRules(item(7, PIECE, false), item(7, PIECE, false)));
    }

    /**
     * A line carries its own factor, so its quantity in base units survives the move -
     * but the unit named on it would no longer be one of the item's, and the item would
     * be stocked in a unit half its history is not in. Refused rather than converted:
     * a silent conversion here rewrites what a past invoice meant.
     */
    @Test
    @DisplayName("a different base unit is refused")
    void differentBaseUnit() {
        assertThrows(BusinessRuleException.class,
                () -> ItemMergeService.checkRules(item(1, PIECE, false), item(2, CARTON, false)));
    }

    /**
     * Expiry dates on the moved lines would point at batches the target does not keep -
     * {@code InvoiceExpiryService} would never offer them and nothing would ever clear
     * them. The other direction is fine: lines with no date are what an item that has
     * just started tracking expiry has anyway.
     */
    @Test
    @DisplayName("expiry tracking may be gained, never lost")
    void expiryTracking() {
        assertThrows(BusinessRuleException.class,
                () -> ItemMergeService.checkRules(item(1, PIECE, true), item(2, PIECE, false)));
        assertDoesNotThrow(() -> ItemMergeService.checkRules(item(1, PIECE, false), item(2, PIECE, true)));
        assertDoesNotThrow(() -> ItemMergeService.checkRules(item(1, PIECE, true), item(2, PIECE, true)));
    }

    /**
     * Everything else is left to the user. Different prices, different groups, different
     * suppliers - a merge is a judgement about two rows being the same thing in the world,
     * and none of those settle it.
     */
    @Test
    @DisplayName("nothing else is a refusal")
    void nothingElseIsRefused() {
        MergeItem source = new MergeItem(1, "شيبسي بالطماطم", "6221", PIECE, false, new BigDecimal("15.000"));
        MergeItem target = new MergeItem(2, "شيبسي أطعم", "6222", PIECE, false, BigDecimal.ZERO);
        assertDoesNotThrow(() -> ItemMergeService.checkRules(source, target));
    }
}
