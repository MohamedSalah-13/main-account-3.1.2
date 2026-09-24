package com.hamza.account.features.unitprices;

/**
 * The four prices an item and each of its units carry.
 * <p>
 * One value per column rather than four copies of every rule: which price a cell edits, which
 * prices "make automatic" clears, which permission a change needs and which price a sale is
 * checked against are all answered by asking the field.
 */
public enum PriceField {
    BUY,
    SELL_1,
    SELL_2,
    SELL_3;

    /** A sale price - every field but the cost. */
    public boolean isSell() {
        return this != BUY;
    }

    /** The price tier a sale price is (V84): {@code SELL_1} is tier 1. Zero for the cost. */
    public int tier() {
        return isSell() ? ordinal() : 0;
    }
}
