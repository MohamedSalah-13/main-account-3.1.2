package com.hamza.account.features.unitprices;

import java.util.EnumSet;
import java.util.Set;

/**
 * One row the operator changed: an item's own prices, or one of its units' own prices.
 *
 * @param itemId the item
 * @param unitId the unit, or {@link #ITEM} when the change is to the item's own prices
 * @param before the prices as the screen read them
 * @param after  the prices as the operator left them
 */
public record PriceChange(int itemId, int unitId, Prices before, Prices after) {

    /** No row of {@code units} has id 0 - the column is {@code AUTO_INCREMENT} from 1. */
    public static final int ITEM = 0;

    /** Below a tenth of a cent two stored prices are one price; {@code DECIMAL(14,2)} holds nothing finer. */
    static final double SAME = 0.0005;

    public PriceChange {
        before = before == null ? Prices.ZERO : before;
        after = after == null ? Prices.ZERO : after;
    }

    public boolean isItem() {
        return unitId == ITEM;
    }

    /**
     * The fields this change actually moves.
     * <p>
     * A save writes these and nothing else, and checks only these against what is stored now. That
     * is what makes a change safe when a figure it carries was never shown: a user without the
     * cost column reads a cost of zero, and writing back the whole row would store that zero.
     */
    public Set<PriceField> changedFields() {
        Set<PriceField> changed = EnumSet.noneOf(PriceField.class);
        for (PriceField field : PriceField.values()) {
            if (!same(before.get(field), after.get(field))) changed.add(field);
        }
        return changed;
    }

    public boolean isEmpty() {
        return changedFields().isEmpty();
    }

    static boolean same(double first, double second) {
        return Math.abs(first - second) < SAME;
    }
}
