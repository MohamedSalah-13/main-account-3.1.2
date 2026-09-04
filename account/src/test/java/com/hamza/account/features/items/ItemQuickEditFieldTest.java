package com.hamza.account.features.items;

import com.hamza.account.features.items.ItemQuickEditField.Rejection;
import com.hamza.account.model.domain.ItemsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The boundaries of an edit made directly in the items table.
 * <p>
 * The one that matters is equality. {@code ItemsService.requireSellAboveBuy} refuses a sale
 * price at or below cost, and the table used to allow exactly-equal - so the operator typed
 * a price, the cell accepted it, and the save then refused it with a different message. A
 * screen that accepts what it cannot save is worse than one that refuses early.
 */
class ItemQuickEditFieldTest {

    private static ItemsModel item(double buy, double sell) {
        ItemsModel model = new ItemsModel();
        model.setBuyPrice(buy);
        model.setSelPrice1(sell);
        return model;
    }

    @Nested
    @DisplayName("a sale at or below cost is refused, exactly as the service refuses it")
    class SellAboveBuy {

        @Test
        void buyPriceMayNotReachTheSellPrice() {
            ItemsModel model = item(10, 15);

            assertNull(ItemQuickEditField.BUY_PRICE.reject(model, 14.99));
            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.BUY_PRICE.reject(model, 15));
            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.BUY_PRICE.reject(model, 15.01));
        }

        @Test
        void sellPriceMayNotFallToTheBuyPrice() {
            ItemsModel model = item(10, 15);

            assertNull(ItemQuickEditField.SELL_PRICE_1.reject(model, 10.01));
            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.SELL_PRICE_1.reject(model, 10));
            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.SELL_PRICE_1.reject(model, 9.99));
        }

        @Test
        @DisplayName("the second and third tiers are held to the same floor")
        void tiersMayNotFallBelowCost() {
            ItemsModel model = item(10, 15);

            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.SELL_PRICE_2.reject(model, 9));
            assertEquals(Rejection.SELL_NOT_ABOVE_BUY, ItemQuickEditField.SELL_PRICE_3.reject(model, 10));
        }

        @Test
        @DisplayName("but zero clears a tier, which is the only way to take one back off an item")
        void zeroClearsATier() {
            ItemsModel model = item(10, 15);

            assertNull(ItemQuickEditField.SELL_PRICE_2.reject(model, 0));
            assertNull(ItemQuickEditField.SELL_PRICE_3.reject(model, 0));
        }

        @Test
        @DisplayName("the first tier is not a tier and may not be cleared - the item would ring up free")
        void theFirstPriceMayNotBeCleared() {
            assertNotNull(ItemQuickEditField.SELL_PRICE_1.reject(item(10, 15), 0));
        }
    }

    @Nested
    class SanityBounds {

        @Test
        void aSlippedDecimalPointIsRefused() {
            ItemsModel model = item(10, 15);

            assertEquals(Rejection.OUT_OF_RANGE,
                    ItemQuickEditField.SELL_PRICE_1.reject(model, ItemQuickEditField.MAXIMUM + 1));
            assertEquals(Rejection.OUT_OF_RANGE,
                    ItemQuickEditField.BUY_PRICE.reject(model, ItemQuickEditField.MAXIMUM * 2));
        }

        @Test
        @DisplayName("a negative price is refused before it can be read as a discount")
        void negativesAreRefused() {
            assertEquals(Rejection.OUT_OF_RANGE, ItemQuickEditField.BUY_PRICE.reject(item(10, 15), -1));
            assertEquals(Rejection.OUT_OF_RANGE, ItemQuickEditField.SELL_PRICE_2.reject(item(10, 15), -1));
        }
    }

    @Test
    @DisplayName("each field reads back exactly the column it wrote")
    void readAndWriteAddressTheSameColumn() {
        ItemsModel model = item(10, 15);
        model.setSelPrice2(20);
        model.setSelPrice3(25);

        for (ItemQuickEditField field : ItemQuickEditField.values()) {
            double before = field.read(model);
            field.write(model, before + 1);
            assertEquals(before + 1, field.read(model), field + " does not read back what it wrote");
            field.write(model, before);
        }
    }
}
