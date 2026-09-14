package com.hamza.account.features.unitprices;

import java.util.Set;

/**
 * The two things the "automatic prices" button can do to a unit's own prices.
 * <p>
 * {@link Mode#AUTOMATIC} is what was agreed as the meaning of "automatic": the field is set to
 * zero, and a unit with no price of its own is priced from the item at the moment of sale - so a
 * carton of twelve stays twelve pieces' price after the piece is repriced tomorrow. Writing the
 * computed figure instead would look identical today and stop following the item for ever.
 * <p>
 * {@link Mode#FIXED} is the opposite and deliberate choice: today's computed figure becomes the
 * unit's own, so it no longer moves with the item.
 */
public final class AutomaticPricing {

    private AutomaticPricing() {
    }

    public enum Mode {
        /** Clear the field, so the unit follows the item. */
        AUTOMATIC,
        /** Store the figure the unit is priced at today as its own. */
        FIXED
    }

    /**
     * The unit's own prices after applying {@code mode} to {@code fields}; fields not named are left
     * exactly as they were.
     *
     * @param itemPrices the item's prices as they stand on screen, pending edits included - fixing a
     *                   carton at twelve times a piece price the operator just typed is what they see
     */
    public static Prices apply(UnitPriceLine unit, Prices itemPrices, Set<PriceField> fields, Mode mode) {
        Prices own = unit.own();
        for (PriceField field : fields) {
            own = switch (mode) {
                case AUTOMATIC -> own.with(field, 0);
                // A field with nothing to fix it at (an item with no second tier) stays unset
                // rather than acquiring a price of zero, which is the same thing stored.
                case FIXED -> own.with(field, unit.effective(field, itemPrices));
            };
        }
        return own;
    }
}
