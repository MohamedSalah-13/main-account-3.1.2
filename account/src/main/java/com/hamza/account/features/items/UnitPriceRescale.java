package com.hamza.account.features.items;

import com.hamza.account.model.domain.ItemsUnitsModel;
import com.hamza.controlsfx.util.NumberUtils;

/**
 * What happens to a unit's own prices when the number of base units it holds changes.
 * <p>
 * <b>A price a unit carries of its own is an absolute figure, not a rate.</b> "The carton
 * sells for 100" says nothing about how many pieces are in the carton, so changing the
 * factor from 6 to 12 leaves the same 100 covering twice the goods - the shop sells a
 * dozen for the price of six, and nothing on the screen says so. The balance stays
 * correct throughout (the line stores the factor it used, and
 * {@code quantity_items_table} multiplies by that), which is exactly what makes this
 * hard to notice: the stock is right and only the money is wrong.
 * <p>
 * A unit priced <em>from the item</em> - every price zero - has no such problem:
 * {@code ItemUnits.sellPrice} computes {@code itemPrice * factor} on the spot, so it
 * follows the new factor by itself. That is why {@link #isNeeded()} asks whether any
 * price is set at all before offering anything.
 * <p>
 * This class decides and computes; it does not ask. The item screen puts the question to
 * the operator, because rescaling is a guess at intent - a carton that got smaller
 * because the supplier repacked it keeps its price, while one whose factor was simply
 * typed wrong wants the price moved with it. Answering "no" is a real answer.
 */
public record UnitPriceRescale(double oldFactor, double newFactor,
                               double buyPrice, double selPrice, double selPrice2, double selPrice3) {

    /** The change {@code row} is about to undergo, read off the row as it stands. */
    public static UnitPriceRescale of(ItemsUnitsModel row, double newFactor) {
        if (row == null) {
            return new UnitPriceRescale(0, newFactor, 0, 0, 0, 0);
        }
        return new UnitPriceRescale(row.getQuantityForUnit(), newFactor,
                row.getBuyPrice(), row.getSelPrice(), row.getSelPrice2(), row.getSelPrice3());
    }

    /**
     * Whether there is anything to ask about: the factor really changed, both values are
     * usable as a ratio, and this unit carries at least one price of its own.
     */
    public boolean isNeeded() {
        return oldFactor > 0 && newFactor > 0 && oldFactor != newFactor && hasOwnPrice();
    }

    /** Whether any of the four prices is set. Zero means "priced from the item". */
    public boolean hasOwnPrice() {
        return buyPrice > 0 || selPrice > 0 || selPrice2 > 0 || selPrice3 > 0;
    }

    /** How much bigger the unit got. 2 when a six becomes a twelve. */
    public double ratio() {
        return oldFactor <= 0 ? 1 : newFactor / oldFactor;
    }

    /**
     * One price at the new factor. Zero stays zero - a unit with no price of its own must
     * not acquire one here, or it would stop following the item's price.
     */
    public double rescaled(double price) {
        if (price <= 0) {
            return 0;
        }
        return NumberUtils.roundToTwoDecimalPlaces(price * ratio());
    }

    public double newBuyPrice() {
        return rescaled(buyPrice);
    }

    public double newSelPrice() {
        return rescaled(selPrice);
    }

    public double newSelPrice2() {
        return rescaled(selPrice2);
    }

    public double newSelPrice3() {
        return rescaled(selPrice3);
    }

    /**
     * Writes the rescaled prices onto {@code row}. The factor itself is not written here -
     * the caller sets it whether or not the prices move, since refusing the rescale still
     * means accepting the new factor.
     */
    public void applyTo(ItemsUnitsModel row) {
        if (row == null || !isNeeded()) {
            return;
        }
        row.setBuyPrice(newBuyPrice());
        row.setSelPrice(newSelPrice());
        row.setSelPrice2(newSelPrice2());
        row.setSelPrice3(newSelPrice3());
    }
}
