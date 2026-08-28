package com.hamza.account.features.stocktransfer;

/**
 * One item moved between warehouses, in the unit it was counted in.
 * <p>
 * {@code quantity} is what was entered - the same shape as an invoice line, which
 * stores what the user typed and the factor separately rather than pre-converting,
 * so the row still means what it meant if the item's own factor changes later. See
 * {@code ItemUnits} and {@code V5__item_units.sql}.
 */
public record StockTransferLine(int itemId, double quantity, int unitId, double typeValue) {
    public StockTransferLine {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
        if (!Double.isFinite(quantity) || quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unitId <= 0) throw new IllegalArgumentException("unitId must be positive");
        if (!Double.isFinite(typeValue) || typeValue <= 0) throw new IllegalArgumentException("typeValue must be positive");
    }

    /** Base-unit quantity - the number to check against, and to write to the stock ledger. */
    public double baseQuantity() {
        return quantity * typeValue;
    }

    /** Compatibility constructor: the item's base unit, factor 1 - every caller before unit conversion existed. */
    public StockTransferLine(int itemId, double quantity) {
        this(itemId, quantity, 1, 1);
    }
}
