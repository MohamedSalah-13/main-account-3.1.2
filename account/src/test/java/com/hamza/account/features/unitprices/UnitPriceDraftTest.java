package com.hamza.account.features.unitprices;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an edit on the unit prices screen means, without the screen: a blank unit price is an
 * automatic one, typing the stored figure back is no edit, and "make automatic" clears where
 * "fix" stores - and neither touches a field it was not asked about.
 */
class UnitPriceDraftTest {

    /** A piece at 8 / 10 / 9.5, sold by a carton of 12 at its own 115, and a box of 6 priced from the item. */
    static UnitPriceItem juice() {
        return new UnitPriceItem(1025, "622100", "عصير مانجو", "قطعة", new Prices(8, 10, 9.5, 0), List.of(
                new UnitPriceLine(2, "كرتونة", 12, new Prices(0, 115, 114, 0)),
                new UnitPriceLine(3, "علبة", 6, Prices.ZERO)));
    }

    private final UnitPriceDraft draft = new UnitPriceDraft();

    @BeforeEach
    void load() {
        draft.load(List.of(juice()));
    }

    @Nested
    @DisplayName("what a unit is priced at")
    class Effective {

        @Test
        @DisplayName("a unit with no price of its own sells at the item price times the factor")
        void automaticIsItemTimesFactor() {
            UnitPriceItem item = draft.item(1025);
            UnitPriceLine box = item.unit(3);

            assertTrue(box.isAutomatic(PriceField.SELL_1));
            assertEquals(60.0, box.effective(PriceField.SELL_1, item.prices()));
            assertEquals(48.0, box.effective(PriceField.BUY, item.prices()));
        }

        @Test
        @DisplayName("a price of its own wins, and an item tier nobody priced stays unpriced on the unit")
        void ownPriceWins() {
            UnitPriceItem item = draft.item(1025);
            UnitPriceLine carton = item.unit(2);

            assertEquals(115.0, carton.effective(PriceField.SELL_1, item.prices()));
            assertEquals(0.0, carton.effective(PriceField.SELL_3, item.prices()));
        }

        @Test
        @DisplayName("an automatic unit follows an edit to the item's price before anything is saved")
        void automaticFollowsAnItemEdit() {
            draft.setItemPrice(1025, PriceField.SELL_1, 11);

            UnitPriceItem item = draft.item(1025);
            assertEquals(66.0, item.unit(3).effective(PriceField.SELL_1, item.prices()));
            assertEquals(115.0, item.unit(2).effective(PriceField.SELL_1, item.prices()), "a manual price does not move");
        }
    }

    @Nested
    @DisplayName("what counts as a change")
    class Changes {

        @Test
        @DisplayName("clearing a unit's price is a change to automatic, carried by the save as zero")
        void clearingIsAChange() {
            draft.setUnitPrice(1025, 2, PriceField.SELL_1, 0);

            assertTrue(draft.isChanged(1025, 2, PriceField.SELL_1));
            assertFalse(draft.isChanged(1025, 2, PriceField.SELL_2));
            PriceChange change = draft.command(9).changes().getFirst();
            assertEquals(2, change.unitId());
            assertEquals(0.0, change.after().sell1());
            assertEquals(Set.of(PriceField.SELL_1), change.changedFields());
        }

        @Test
        @DisplayName("typing the stored figure back is no edit at all")
        void backToStoredIsNoChange() {
            draft.setUnitPrice(1025, 2, PriceField.SELL_1, 120);
            draft.setUnitPrice(1025, 2, PriceField.SELL_1, 115);

            assertFalse(draft.hasChanges());
            assertTrue(draft.command(9).changes().isEmpty());
        }

        @Test
        @DisplayName("a typed figure is rounded to the cent the column stores")
        void roundedToTheCent() {
            draft.setItemPrice(1025, PriceField.SELL_1, 10.456);

            assertEquals(10.46, draft.item(1025).prices().sell1());
        }

        @Test
        @DisplayName("an item edit and a unit edit are two rows of one save")
        void itemAndUnitAreTwoRows() {
            draft.setItemPrice(1025, PriceField.SELL_1, 11);
            draft.setUnitPrice(1025, 3, PriceField.SELL_1, 62);

            UnitPriceSaveCommand command = draft.command(9);
            assertEquals(2, draft.changedRowCount());
            assertTrue(command.touchesItems());
            assertTrue(command.touchesUnits());
            assertFalse(command.touchesCost());
            assertEquals(9, command.userId());
        }

        @Test
        @DisplayName("discard puts every figure back")
        void discard() {
            draft.setItemPrice(1025, PriceField.SELL_1, 11);
            draft.discard();

            assertFalse(draft.hasChanges());
            assertEquals(10.0, draft.item(1025).prices().sell1());
        }
    }

    @Nested
    @DisplayName("automatic prices")
    class Automatic {

        private final List<UnitPriceDraft.UnitRef> both = List.of(
                new UnitPriceDraft.UnitRef(1025, 2), new UnitPriceDraft.UnitRef(1025, 3));

        @Test
        @DisplayName("automatic clears the chosen prices and leaves the others as they were")
        void automaticClearsOnlyTheChosenFields() {
            int changed = draft.applyPricing(both, EnumSet.of(PriceField.SELL_1), AutomaticPricing.Mode.AUTOMATIC);

            UnitPriceLine carton = draft.item(1025).unit(2);
            assertEquals(1, changed, "the box was already automatic");
            assertEquals(0.0, carton.own().sell1());
            assertEquals(114.0, carton.own().sell2(), "a price not asked about keeps its figure");
        }

        @Test
        @DisplayName("fix stores today's computed figure, so the unit stops following the item")
        void fixedStoresTheFigure() {
            draft.applyPricing(both, EnumSet.of(PriceField.SELL_1, PriceField.SELL_3), AutomaticPricing.Mode.FIXED);

            UnitPriceLine box = draft.item(1025).unit(3);
            assertEquals(60.0, box.own().sell1());
            assertFalse(box.isAutomatic(PriceField.SELL_1));
            assertEquals(0.0, box.own().sell3(), "nothing to fix a tier the item has no price for at");
        }

        @Test
        @DisplayName("the preview lists before and after and changes nothing")
        void previewDoesNotChange() {
            List<UnitPriceDraft.PreviewRow> rows =
                    draft.preview(both, EnumSet.of(PriceField.SELL_1), AutomaticPricing.Mode.AUTOMATIC);

            assertEquals(1, rows.size());
            UnitPriceDraft.PreviewRow row = rows.getFirst();
            assertEquals(115.0, row.before());
            assertFalse(row.beforeAutomatic());
            assertEquals(120.0, row.after(), "twelve pieces at ten");
            assertTrue(row.afterAutomatic());
            assertFalse(draft.hasChanges());
        }

        @Test
        @DisplayName("every unit of every loaded item is what 'this page' covers")
        void allUnits() {
            assertEquals(both, draft.allUnits());
        }
    }
}
