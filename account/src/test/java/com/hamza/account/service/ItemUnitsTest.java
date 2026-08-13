package com.hamza.account.service;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The factor an item converts by is its own. These tests are the reason the
 * conversion moved out of the invoice controllers: the same two units mean
 * different multiples on two different items, and nothing in the old code
 * could express that.
 */
class ItemUnitsTest {

    private static final UnitsModel PIECE = new UnitsModel(1, "قطعة", 1);

    /**
     * A unit as {@code units} holds it - one factor for the whole database,
     * which is exactly what an item's own factor has to override.
     */
    private static UnitsModel globalUnit(int id, String name, double valueD) {
        return new UnitsModel(id, name, valueD);
    }

    private static ItemsModel item(UnitsModel base, ItemsUnitsModel... extras) {
        return item(base, null, extras);
    }

    private static ItemsModel item(UnitsModel base, String itemBarcode, ItemsUnitsModel... extras) {
        ItemsModel item = new ItemsModel();
        item.setId(7);
        item.setBarcode(itemBarcode);
        item.setUnitsType(base);

        // ItemsDao prepends the base unit with a factor of 1, then appends the
        // item's own rows; mirror that here.
        List<ItemsUnitsModel> rows = new ArrayList<>();
        ItemsUnitsModel baseRow = new ItemsUnitsModel();
        baseRow.setUnitsModel(base);
        baseRow.setQuantityForUnit(1);
        baseRow.setItemsBarcode(itemBarcode);
        rows.add(baseRow);
        rows.addAll(List.of(extras));
        item.setItemsUnitsModelList(rows);
        return item;
    }

    private static ItemsUnitsModel row(UnitsModel unit, double factorForThisItem) {
        ItemsUnitsModel row = new ItemsUnitsModel();
        row.setUnitsModel(unit);
        row.setQuantityForUnit(factorForThisItem);
        return row;
    }

    /** A unit priced outright: zero on any of these means "priced from the item". */
    private static ItemsUnitsModel pricedRow(UnitsModel unit, double factorForThisItem,
                                             double buy, double sel1, double sel2, double sel3) {
        ItemsUnitsModel row = row(unit, factorForThisItem);
        row.setBuyPrice(buy);
        row.setSelPrice(sel1);
        row.setSelPrice2(sel2);
        row.setSelPrice3(sel3);
        return row;
    }

    @Nested
    @DisplayName("the item decides the factor")
    class PerItemFactor {

        @Test
        @DisplayName("the same unit is a different multiple on two items")
        void sameUnitDiffersPerItem() {
            UnitsModel carton = globalUnit(2, "كرتونة", 12);

            ItemsModel juice = item(PIECE, row(carton, 12));
            ItemsModel cigarettes = item(PIECE, row(carton, 200));

            assertEquals(12, ItemUnits.unitByName(juice, "كرتونة").getValue());
            assertEquals(200, ItemUnits.unitByName(cigarettes, "كرتونة").getValue());
        }

        @Test
        @DisplayName("units.value_d does not leak into the item's factor")
        void ignoresTheGlobalValue() {
            UnitsModel carton = globalUnit(2, "كرتونة", 12);
            ItemsModel item = item(PIECE, row(carton, 200));

            assertEquals(200, ItemUnits.unitByName(item, "كرتونة").getValue());
            // and the row the item was built from is left as it was
            assertEquals(12, carton.getValue());
        }

        @Test
        @DisplayName("two units of one item may hold the same number of base units")
        void allowsRepeatedFactors() {
            ItemsModel item = item(PIECE,
                    row(globalUnit(2, "كرتونة", 12), 12),
                    row(globalUnit(3, "لفة", 1), 12));

            assertEquals(3, ItemUnits.unitsFor(item).size());
            assertEquals(12, ItemUnits.unitByName(item, "لفة").getValue());
        }

        @Test
        @DisplayName("a row with no factor of its own falls back to the unit's")
        void fallsBackToTheGlobalValue() {
            ItemsUnitsModel unset = row(globalUnit(2, "كرتونة", 12), 0);
            ItemsModel item = item(PIECE, unset);

            assertEquals(12, ItemUnits.unitByName(item, "كرتونة").getValue());
        }
    }

    @Nested
    @DisplayName("the base unit")
    class BaseUnit {

        @Test
        @DisplayName("is first, and converts one to one")
        void baseIsFirstAndUnscaled() {
            ItemsModel item = item(PIECE, row(globalUnit(2, "كرتونة", 12), 12));

            assertEquals("قطعة", ItemUnits.baseUnit(item).getUnit_name());
            assertEquals(1, ItemUnits.factor(ItemUnits.baseUnit(item)));
        }

        @Test
        @DisplayName("stands in for a unit the item does not sell in")
        void unknownUnitFallsBackToBase() {
            ItemsModel item = item(PIECE, row(globalUnit(2, "كرتونة", 12), 12));

            // Another item's unit must not convert this one by that item's factor.
            assertEquals("قطعة", ItemUnits.unitByName(item, "طن").getUnit_name());
            assertEquals("قطعة", ItemUnits.unitByName(item, null).getUnit_name());
        }

        @Test
        @DisplayName("is still available on an item carrying no unit rows")
        void itemWithoutRows() {
            ItemsModel item = new ItemsModel();
            item.setUnitsType(globalUnit(2, "كرتونة", 12));
            item.setItemsUnitsModelList(new ArrayList<>());

            List<UnitsModel> units = ItemUnits.unitsFor(item);
            assertEquals(1, units.size());
            // Its own unit is its base, whatever units.value_d says.
            assertEquals(1, units.getFirst().getValue());
        }

        @Test
        @DisplayName("is absent, not invented, for an item with no unit at all")
        void itemWithNothing() {
            assertTrue(ItemUnits.unitsFor(new ItemsModel()).isEmpty());
            assertNull(ItemUnits.baseUnit(new ItemsModel()));
            assertTrue(ItemUnits.unitsFor(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("a price per unit")
    class Pricing {

        private static final UnitsModel CARTON = globalUnit(2, "كرتونة", 12);

        @Test
        @DisplayName("a unit priced by hand is not twelve times the piece")
        void ownPriceWins() {
            // 12 pieces at 10 would be 120; the carton is deliberately sold at 100.
            ItemsModel item = item(PIECE, pricedRow(CARTON, 12, 90, 100, 0, 0));

            assertEquals(100, ItemUnits.sellPrice(item, ItemUnits.unitByName(item, "كرتونة"), 1, 10));
            assertEquals(90, ItemUnits.buyPrice(item, ItemUnits.unitByName(item, "كرتونة"), 7.5));
            assertTrue(ItemUnits.hasOwnBuyPrice(item, ItemUnits.unitByName(item, "كرتونة")));
        }

        @Test
        @DisplayName("a unit with no price of its own scales the item's")
        void fallsBackToTheItem() {
            ItemsModel item = item(PIECE, row(CARTON, 12));

            assertEquals(120, ItemUnits.sellPrice(item, ItemUnits.unitByName(item, "كرتونة"), 1, 10));
            assertEquals(90, ItemUnits.buyPrice(item, ItemUnits.unitByName(item, "كرتونة"), 7.5));
            assertFalse(ItemUnits.hasOwnSellPrice(item, ItemUnits.unitByName(item, "كرتونة"), 1));
        }

        @Test
        @DisplayName("each price tier answers for itself")
        void tiersAreSeparate() {
            ItemsModel item = item(PIECE, pricedRow(CARTON, 12, 0, 100, 95, 0));
            UnitsModel carton = ItemUnits.unitByName(item, "كرتونة");

            assertEquals(100, ItemUnits.sellPrice(item, carton, 1, 10));
            assertEquals(95, ItemUnits.sellPrice(item, carton, 2, 9));
            // Tier 3 was left unset, so it comes from the item's tier-3 price.
            assertEquals(96, ItemUnits.sellPrice(item, carton, 3, 8));
            assertFalse(ItemUnits.hasOwnSellPrice(item, carton, 3));
        }

        @Test
        @DisplayName("the base unit is priced by the item, never overridden")
        void baseUnitUsesTheItemPrice() {
            ItemsModel item = item(PIECE, pricedRow(CARTON, 12, 90, 100, 0, 0));
            UnitsModel piece = ItemUnits.baseUnit(item);

            assertEquals(10, ItemUnits.sellPrice(item, piece, 1, 10));
            assertEquals(7.5, ItemUnits.buyPrice(item, piece, 7.5));
            assertFalse(ItemUnits.hasOwnSellPrice(item, piece, 1));
        }

        @Test
        @DisplayName("one item's price does not reach another's unit")
        void unknownUnitIsPricedAsBase() {
            ItemsModel item = item(PIECE, pricedRow(CARTON, 12, 90, 100, 0, 0));

            // unitByName falls back to the base unit, which is priced from the item.
            assertEquals(10, ItemUnits.sellPrice(item, ItemUnits.unitByName(item, "طن"), 1, 10));
        }
    }

    @Nested
    @DisplayName("a barcode per unit")
    class Barcodes {

        private static ItemsUnitsModel barcodedRow(UnitsModel unit, double factor, String barcode) {
            ItemsUnitsModel row = row(unit, factor);
            row.setItemsBarcode(barcode);
            return row;
        }

        @Test
        @DisplayName("the code on the carton selects the carton")
        void unitBarcodeSelectsItsUnit() {
            ItemsModel item = item(PIECE, "1000",
                    barcodedRow(globalUnit(2, "كرتونة", 12), 12, "2000"));

            UnitsModel scanned = ItemUnits.unitByBarcode(item, "2000");
            assertEquals("كرتونة", scanned.getUnit_name());
            assertEquals(12, scanned.getValue());
        }

        @Test
        @DisplayName("the item's own code stays on the base unit")
        void itemBarcodeSelectsTheBase() {
            ItemsModel item = item(PIECE, "1000",
                    barcodedRow(globalUnit(2, "كرتونة", 12), 12, "2000"));

            assertEquals("قطعة", ItemUnits.unitByBarcode(item, "1000").getUnit_name());
        }

        @Test
        @DisplayName("each unit answers to its own code")
        void severalUnitsSeveralCodes() {
            ItemsModel item = item(PIECE, "1000",
                    barcodedRow(globalUnit(2, "كرتونة", 12), 12, "2000"),
                    barcodedRow(globalUnit(3, "لفة", 1), 6, "3000"));

            assertEquals(12, ItemUnits.unitByBarcode(item, "2000").getValue());
            assertEquals(6, ItemUnits.unitByBarcode(item, "3000").getValue());
        }

        @Test
        @DisplayName("a code nobody claims falls back to the base unit")
        void unknownCode() {
            ItemsModel item = item(PIECE, "1000",
                    barcodedRow(globalUnit(2, "كرتونة", 12), 12, "2000"));

            assertEquals("قطعة", ItemUnits.unitByBarcode(item, "9999").getUnit_name());
            assertEquals("قطعة", ItemUnits.unitByBarcode(item, null).getUnit_name());
            assertEquals("قطعة", ItemUnits.unitByBarcode(item, "  ").getUnit_name());
        }

        @Test
        @DisplayName("a unit without a code is not matched by an empty one")
        void unitWithoutCode() {
            // Units with no barcode store NULL, and a blank scan must not pick one.
            ItemsModel item = item(PIECE, "1000", row(globalUnit(2, "كرتونة", 12), 12));

            assertEquals("قطعة", ItemUnits.unitByBarcode(item, "").getUnit_name());
        }
    }

    @Nested
    @DisplayName("conversion")
    class Conversion {

        @Test
        @DisplayName("scales to and from base units")
        void roundTrip() {
            UnitsModel carton = globalUnit(2, "كرتونة", 200);

            assertEquals(600, ItemUnits.toBase(3, carton));
            assertEquals(3, ItemUnits.fromBase(600, carton));
        }

        @Test
        @DisplayName("a missing or non-positive factor converts one to one")
        void guardsAgainstZero() {
            // A zero factor would zero the stock movement; a negative one would
            // reverse it. Neither may reach the balance.
            assertEquals(5, ItemUnits.toBase(5, null));
            assertEquals(5, ItemUnits.toBase(5, globalUnit(9, "خاطئة", 0)));
            assertEquals(5, ItemUnits.fromBase(5, globalUnit(9, "خاطئة", -3)));
        }
    }
}
