package com.hamza.account.model.domain;

import java.math.BigDecimal;

/**
 * One line of the dashboard's best sellers.
 *
 * @param totalQuantity net of returns, in the item's base unit - {@code unitName}
 * @param averagePrice  what one base unit sold for, after each line's own discount
 */
public record TopSellingItem(String itemName, BigDecimal totalQuantity, BigDecimal averagePrice, String unitName) {
}
