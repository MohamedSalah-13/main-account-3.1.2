package com.hamza.account.features.pricing;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.service.ItemUnits;

/**
 * What one of a unit sells for on a tier - the list price a sales line starts from.
 * <p>
 * A unit's own price on the tier comes first; with none (zero, which V6 wrote for every unit) it is
 * the item's price on the tier times the unit's factor, as it always was. <b>And with neither, the
 * item's tier-1 price stands in, and says so</b> (docs/pricing-and-offers-plan.md ق-س٣): tier 1 is the
 * price the item screen holds every item to, so it is the one price every item has. The line used to
 * come out at zero and be refused as "an invalid line", which told the cashier nothing about why and
 * stopped a sale for a gap in the data.
 */
public final class PriceResolver {

    /** The item's own price on a tier, per base unit - {@link PriceTiers#itemPrice} unless a caller has its own. */
    @FunctionalInterface
    public interface ItemPrice {
        double of(ItemsModel item, int tierId);
    }

    private PriceResolver() {
    }

    public static ListedPrice resolve(ItemsModel item, UnitsModel unit, int tierId) {
        return resolve(item, unit, tierId, PriceTiers::itemPrice);
    }

    /**
     * @param itemPrice the item's own price on a tier - the invoice screens pass their family's, which
     *                  is what their tests stub
     */
    public static ListedPrice resolve(ItemsModel item, UnitsModel unit, int tierId, ItemPrice itemPrice) {
        int asked = PriceTiers.orFirst(tierId);
        ListedPrice onTier = onTier(item, unit, asked, itemPrice);
        if (onTier.price() > 0 || asked == PriceTiers.FIRST) {
            return onTier;
        }
        ListedPrice first = onTier(item, unit, PriceTiers.FIRST, itemPrice);
        return new ListedPrice(first.price(), asked, ListedPrice.Source.FIRST_TIER);
    }

    private static ListedPrice onTier(ItemsModel item, UnitsModel unit, int tierId, ItemPrice itemPrice) {
        double own = PriceTiers.unitPrice(ownRow(item, unit), tierId);
        if (own > 0) {
            return new ListedPrice(own, tierId, ListedPrice.Source.UNIT_OWN);
        }
        double price = itemPrice.of(item, tierId) * ItemUnits.factor(unit);
        return new ListedPrice(Math.max(0, price), tierId, ListedPrice.Source.ITEM_TIMES_FACTOR);
    }

    /** The item's row for this unit, or null - the base unit is {@code items.unit_id}, not a row. */
    private static ItemsUnitsModel ownRow(ItemsModel item, UnitsModel unit) {
        if (item == null || unit == null || item.getItemsUnitsModelList() == null) {
            return null;
        }
        for (ItemsUnitsModel row : item.getItemsUnitsModelList()) {
            if (row.getUnitsModel() != null && row.getUnitsModel().getUnit_id() == unit.getUnit_id()) {
                return row;
            }
        }
        return null;
    }
}
