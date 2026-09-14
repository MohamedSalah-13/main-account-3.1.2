package com.hamza.account.features.unitprices;

/**
 * A cost and three sale prices, as stored.
 * <p>
 * On an item every figure is the price of one base unit. On a unit a figure is that unit's
 * <em>own</em> price, and {@code 0} means it has none and is priced from the item - see
 * {@link UnitPriceLine#effective}. That is the meaning {@code items_units} has had since V6, and
 * {@code ItemUnits.sellPrice} is what reads it at the till.
 */
public record Prices(double buy, double sell1, double sell2, double sell3) {

    public static final Prices ZERO = new Prices(0, 0, 0, 0);

    public double get(PriceField field) {
        return switch (field) {
            case BUY -> buy;
            case SELL_1 -> sell1;
            case SELL_2 -> sell2;
            case SELL_3 -> sell3;
        };
    }

    public Prices with(PriceField field, double value) {
        return switch (field) {
            case BUY -> new Prices(value, sell1, sell2, sell3);
            case SELL_1 -> new Prices(buy, value, sell2, sell3);
            case SELL_2 -> new Prices(buy, sell1, value, sell3);
            case SELL_3 -> new Prices(buy, sell1, sell2, value);
        };
    }

    /** Whether any of the four is set. On a unit: whether it carries a price of its own at all. */
    public boolean anySet() {
        return buy > 0 || sell1 > 0 || sell2 > 0 || sell3 > 0;
    }
}
