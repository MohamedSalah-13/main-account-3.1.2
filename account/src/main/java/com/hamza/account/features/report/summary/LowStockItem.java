package com.hamza.account.features.report.summary;

import com.hamza.account.features.notification.StockLevel;

import java.math.BigDecimal;

/**
 * An item the low stock notification would name: at or below its minimum, out of stock, or below zero -
 * the whole business's balance, in its base unit.
 */
public record LowStockItem(int itemId, String name, String unitName, BigDecimal minimum, BigDecimal balance) {

    public LowStockItem {
        name = name == null ? "" : name;
        unitName = unitName == null ? "" : unitName;
        minimum = minimum == null ? BigDecimal.ZERO : minimum;
        balance = balance == null ? BigDecimal.ZERO : balance;
    }

    /** How it stands, by the rule the sale-time alert applies: a minimum of zero is none set. */
    public StockLevel level() {
        return StockLevel.of(balance.doubleValue(), minimum.doubleValue());
    }

    public boolean hasMinimum() {
        return minimum.signum() > 0;
    }
}
