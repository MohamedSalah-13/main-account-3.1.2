package com.hamza.account.service;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;

import java.util.ArrayList;
import java.util.List;

/**
 * The units an item may be bought or sold in, and the factor that converts each
 * of them to the item's base unit.
 *
 * <p>The factor is per item: it comes from {@code items_units.quantity}, so a
 * carton of juice can be 12 while a carton of cigarettes is 200. {@code units}
 * still carries {@code value_d}, but only as the fallback for a row that has no
 * factor of its own - it is one number for the whole database and cannot answer
 * the question for two different items.
 *
 * <p>Every {@link UnitsModel} handed out here is a fresh instance whose
 * {@code value} is that item's factor. The invoice stores the factor it used in
 * {@code type_value} on the line, and the balance views multiply by that stored
 * value, so an item's factor changing later does not rewrite its history.
 */
public final class ItemUnits {

    private ItemUnits() {
    }

    /**
     * The item's units, base unit first. Never empty: an item with no extra
     * units still sells in the one it was defined with.
     */
    public static List<UnitsModel> unitsFor(ItemsModel item) {
        List<UnitsModel> units = new ArrayList<>();
        if (item == null) {
            return units;
        }

        List<ItemsUnitsModel> rows = item.getItemsUnitsModelList();
        if (rows != null) {
            for (ItemsUnitsModel row : rows) {
                UnitsModel unit = row.getUnitsModel();
                if (unit == null) continue;
                units.add(new UnitsModel(unit.getUnit_id(), unit.getUnit_name(), factorOf(row, unit)));
            }
        }

        // ItemsDao prepends the base unit to the list, but an item built by hand
        // (the Excel import, a search result) may carry only items.unit_id.
        if (units.isEmpty() && item.getUnitsType() != null) {
            UnitsModel base = item.getUnitsType();
            units.add(new UnitsModel(base.getUnit_id(), base.getUnit_name(), 1));
        }
        return units;
    }

    /**
     * The unit this item is stocked and priced in - the one everything else
     * converts to. Null only for an item with no unit at all.
     */
    public static UnitsModel baseUnit(ItemsModel item) {
        List<UnitsModel> units = unitsFor(item);
        return units.isEmpty() ? null : units.getFirst();
    }

    /**
     * The named unit as it applies to this item, or its base unit when the name
     * is unknown to it - a unit the item does not define must not silently
     * convert by some other item's factor.
     */
    public static UnitsModel unitByName(ItemsModel item, String unitName) {
        if (unitName == null) {
            return baseUnit(item);
        }
        return unitsFor(item).stream()
                .filter(unit -> unitName.equals(unit.getUnit_name()))
                .findFirst()
                .orElseGet(() -> baseUnit(item));
    }

    /**
     * The unit whose own barcode was scanned, or the item's base unit for the
     * item's own code and for anything else.
     *
     * <p>This is what makes a carton scan sell a carton: the code identifies the
     * unit as much as the item, and without it a carton scanned at the till goes
     * on the invoice as one piece at the piece price.
     */
    public static UnitsModel unitByBarcode(ItemsModel item, String barcode) {
        if (item == null || barcode == null || barcode.isBlank() || item.getItemsUnitsModelList() == null) {
            return baseUnit(item);
        }

        String scanned = barcode.trim();
        return item.getItemsUnitsModelList().stream()
                .filter(row -> scanned.equals(row.getItemsBarcode()) && row.getUnitsModel() != null)
                .findFirst()
                .map(row -> new UnitsModel(row.getUnitsModel().getUnit_id(),
                        row.getUnitsModel().getUnit_name(),
                        factorOf(row, row.getUnitsModel())))
                .orElseGet(() -> baseUnit(item));
    }

    /**
     * A quantity expressed in {@code unit}, converted to base units - what the
     * balance is held in and what stock checks have to compare against.
     */
    public static double toBase(double quantity, UnitsModel unit) {
        return quantity * factor(unit);
    }

    /**
     * A quantity held in base units, expressed in {@code unit}.
     */
    public static double fromBase(double baseQuantity, UnitsModel unit) {
        return baseQuantity / factor(unit);
    }

    /**
     * The conversion factor of a unit, guarded so a missing or non-positive
     * value converts one-to-one instead of zeroing or inverting a quantity.
     */
    public static double factor(UnitsModel unit) {
        if (unit == null || unit.getValue() <= 0) {
            return 1;
        }
        return unit.getValue();
    }

    /**
     * What one of {@code unit} sells for on price tier {@code priceType}.
     *
     * <p>A unit may carry its own price, which is the point of selling by the
     * carton: twelve pieces bought together are cheaper than twelve pieces
     * bought one at a time. Where it carries none - zero, which is what every
     * unit held before prices were readable - it is priced as the item's own
     * price times the factor, exactly as before.
     *
     * @param itemPrice the item's price for that tier, per base unit
     */
    public static double sellPrice(ItemsModel item, UnitsModel unit, int priceType, double itemPrice) {
        double own = ownSellPrice(rowFor(item, unit), priceType);
        return own > 0 ? own : itemPrice * factor(unit);
    }

    /**
     * What one of {@code unit} costs, on the same terms as
     * {@link #sellPrice}. This is the floor a sale may not go below, so a unit
     * priced by hand has to answer here too - otherwise a discounted carton
     * would be rejected for undercutting twelve times the piece cost.
     */
    public static double buyPrice(ItemsModel item, UnitsModel unit, double itemBuyPrice) {
        ItemsUnitsModel row = rowFor(item, unit);
        double own = row == null ? 0 : row.getBuyPrice();
        return own > 0 ? own : itemBuyPrice * factor(unit);
    }

    /**
     * Whether this unit is priced by hand rather than derived from the item.
     * Code that works backwards from a line's price to the item's - the invoice
     * option that updates an item's price as you type one - has to leave those
     * alone; dividing an outright carton price by twelve is not the piece price.
     */
    public static boolean hasOwnSellPrice(ItemsModel item, UnitsModel unit, int priceType) {
        return ownSellPrice(rowFor(item, unit), priceType) > 0;
    }

    /** Whether this unit carries a purchase price independent of the base unit. */
    public static boolean hasOwnBuyPrice(ItemsModel item, UnitsModel unit) {
        ItemsUnitsModel row = rowFor(item, unit);
        return row != null && row.getBuyPrice() > 0;
    }

    private static double ownSellPrice(ItemsUnitsModel row, int priceType) {
        if (row == null) {
            return 0;
        }
        return switch (priceType) {
            case 2 -> row.getSelPrice2();
            case 3 -> row.getSelPrice3();
            default -> row.getSelPrice();
        };
    }

    /**
     * The item's row for this unit, or null - the base unit has none, since it
     * is {@code items.unit_id} rather than a row in {@code items_units}.
     */
    private static ItemsUnitsModel rowFor(ItemsModel item, UnitsModel unit) {
        if (item == null || unit == null || item.getItemsUnitsModelList() == null) {
            return null;
        }
        return item.getItemsUnitsModelList().stream()
                .filter(row -> row.getUnitsModel() != null
                        && row.getUnitsModel().getUnit_id() == unit.getUnit_id())
                .findFirst()
                .orElse(null);
    }

    private static double factorOf(ItemsUnitsModel row, UnitsModel unit) {
        double perItem = row.getQuantityForUnit();
        return perItem > 0 ? perItem : factor(unit);
    }
}
