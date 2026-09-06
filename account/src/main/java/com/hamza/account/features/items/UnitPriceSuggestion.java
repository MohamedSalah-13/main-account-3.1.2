package com.hamza.account.features.items;

import com.hamza.controlsfx.util.NumberUtils;

/**
 * What a unit would sell or cost for at a given factor, if it carried no price of its own.
 * <p>
 * It is the same arithmetic {@code ItemUnits.sellPrice}/{@code buyPrice} already fall back
 * to when a unit's own price is zero - {@code itemPrice * factor} - said early enough for
 * the operator to see it while typing the factor, instead of finding out at the till.
 * <p>
 * <b>This is a hint, not a value to store.</b> A blank price field is what makes a unit
 * follow the item: leave it empty and a carton of twelve is always twelve times whatever
 * the piece costs today, including after the item is repriced. Writing the number into the
 * field would make it the unit's own price and stop that - which is the whole problem
 * {@link UnitPriceRescale} exists to catch. So the screen shows this as prompt text, in
 * the field's grey, and stores nothing.
 */
public final class UnitPriceSuggestion {

    private UnitPriceSuggestion() {
    }

    /**
     * The item's price scaled to one of this unit, or {@code 0} when there is nothing to
     * suggest - no price on the item yet, or no usable factor. Zero means "say nothing"
     * rather than "free": a suggestion of 0.00 would read as a price.
     */
    public static double forFactor(double itemPrice, double factor) {
        if (itemPrice <= 0 || factor <= 0) {
            return 0;
        }
        return NumberUtils.roundToTwoDecimalPlaces(itemPrice * factor);
    }

    /**
     * A figure as a person would write it - {@code 240} rather than {@code 240.0}, while
     * {@code 12.5} keeps its half. Prompt text is read at a glance, and a trailing
     * {@code .0} on every suggestion is noise in a column of them.
     */
    public static String plain(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
