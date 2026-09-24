package com.hamza.account.features.pricing;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The juice of the plan's §0: a piece at 10 / 9.50 / 9 on the three tiers, a carton of 12 at 115 / 110
 * with no wholesale price of its own.
 */
class PriceResolverTest {

    static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);
    static final UnitsModel CARTON = new UnitsModel(2, "كرتونة", 12);

    static ItemsModel juice(double retail, double trade, double wholesale) {
        ItemsModel item = new ItemsModel(7, "J7", "عصير 250 مل");
        item.setSelPrice1(retail);
        item.setSelPrice2(trade);
        item.setSelPrice3(wholesale);
        item.setBuyPrice(8);
        List<ItemsUnitsModel> units = new ArrayList<>();
        ItemsUnitsModel base = new ItemsUnitsModel();
        base.setUnitsModel(PIECE);
        base.setQuantityForUnit(1);
        units.add(base);
        ItemsUnitsModel carton = new ItemsUnitsModel();
        carton.setUnitsModel(CARTON);
        carton.setQuantityForUnit(12);
        carton.setSelPrice(115);
        carton.setSelPrice2(110);
        units.add(carton);
        item.setItemsUnitsModelList(units);
        return item;
    }

    @Nested
    @DisplayName("PriceTiers: the one switch")
    class TheSwitch {

        @Test
        void readsAndWritesEachTier() {
            ItemsModel item = juice(10, 9.5, 9);
            assertEquals(10, PriceTiers.itemPrice(item, 1));
            assertEquals(9.5, PriceTiers.itemPrice(item, 2));
            assertEquals(9, PriceTiers.itemPrice(item, 3));
            PriceTiers.setItemPrice(item, 3, 8.75);
            assertEquals(8.75, item.getSelPrice3());
        }

        @Test
        @DisplayName("an id it does not know reads as tier 1, as every old switch's default did")
        void unknownIsFirst() {
            ItemsModel item = juice(10, 9.5, 9);
            assertEquals(10, PriceTiers.itemPrice(item, 0));
            assertEquals(10, PriceTiers.itemPrice(item, 4));
            assertEquals(1, PriceTiers.orFirst(-1));
            assertFalse(PriceTiers.exists(0));
            assertTrue(PriceTiers.exists(3));
        }

        @Test
        @DisplayName("tier 1 of a unit is sel_price, with no digit, since V1")
        void columns() {
            assertEquals("sel_price1", PriceTiers.itemColumn(1));
            assertEquals("sel_price3", PriceTiers.itemColumn(3));
            assertEquals("sel_price", PriceTiers.unitColumn(1));
            assertEquals("sel_price2", PriceTiers.unitColumn(2));
            assertEquals("sel_price1", PriceTiers.itemColumn(9));
        }
    }

    @Test
    @DisplayName("a unit's own price on the tier comes first")
    void unitOwnPrice() {
        ListedPrice listed = PriceResolver.resolve(juice(10, 9.5, 9), CARTON, 2);
        assertEquals(110, listed.price());
        assertEquals(ListedPrice.Source.UNIT_OWN, listed.source());
        assertFalse(listed.fromFirstTier());
    }

    @Test
    @DisplayName("a unit with none is the item's tier price times its factor")
    void itemTimesFactor() {
        ListedPrice listed = PriceResolver.resolve(juice(10, 9.5, 9), CARTON, 3);
        assertEquals(108, listed.price(), 1e-9);
        assertEquals(ListedPrice.Source.ITEM_TIMES_FACTOR, listed.source());
        assertEquals(9.5, PriceResolver.resolve(juice(10, 9.5, 9), PIECE, 2).price());
    }

    @Test
    @DisplayName("no price on the tier at all: tier 1's stands in, and says so")
    void fallsBackToTheFirstTier() {
        ItemsModel noWholesale = juice(10, 9.5, 0);
        ListedPrice piece = PriceResolver.resolve(noWholesale, PIECE, 3);
        assertEquals(10, piece.price());
        assertTrue(piece.fromFirstTier());
        assertEquals(3, piece.tierId(), "the tier asked for is kept");

        // The carton has its own tier-1 price, and that is what stands in - not 12 pieces at tier 1.
        ListedPrice carton = PriceResolver.resolve(noWholesale, CARTON, 3);
        assertEquals(115, carton.price());
        assertTrue(carton.fromFirstTier());
    }

    @Test
    @DisplayName("tier 1 itself has nothing to fall back to, and is never marked")
    void firstTierIsNeverAFallback() {
        ListedPrice listed = PriceResolver.resolve(juice(0, 9.5, 9), PIECE, 1);
        assertEquals(0, listed.price());
        assertFalse(listed.fromFirstTier());
        assertFalse(PriceResolver.resolve(juice(10, 0, 0), PIECE, 0).fromFirstTier(),
                "an unknown tier is tier 1, not a fallback to it");
    }

    @Test
    @DisplayName("a caller's own item price is the one used - the invoice families pass theirs")
    void itemPriceSeam() {
        ListedPrice listed = PriceResolver.resolve(juice(10, 9.5, 9), PIECE, 2, (item, tier) -> tier == 2 ? 0 : 4);
        assertEquals(4, listed.price());
        assertTrue(listed.fromFirstTier());
    }
}
