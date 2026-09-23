package com.hamza.account.features.report.itemsales;

import java.math.BigDecimal;

/**
 * One item's lines over a period, gathered by the unit they were written in and the price they carried -
 * what the daily report used to list, now inside the item's row. A carton and a piece are two lines here
 * because they are two prices; on the row above they are one quantity in the base unit.
 *
 * @param isReturn  whether these are sales returns' lines
 * @param unitName  the unit the lines were written in
 * @param factor    how many base units one of that unit is, as the lines stored it
 * @param price     the price one of that unit carried
 * @param quantity  how many of that unit, as written
 * @param discount  the lines' own discounts
 * @param amount    the lines' amount after their own discounts - what adds up to the row
 * @param documents how many documents carried such a line
 */
public record ItemSalesLine(boolean isReturn, String unitName, BigDecimal factor, BigDecimal price,
                            BigDecimal quantity, BigDecimal discount, BigDecimal amount, int documents) {

    public ItemSalesLine {
        unitName = unitName == null ? "" : unitName;
        factor = factor == null ? BigDecimal.ONE : factor;
        price = price == null ? BigDecimal.ZERO : price;
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        discount = discount == null ? BigDecimal.ZERO : discount;
        amount = amount == null ? BigDecimal.ZERO : amount;
    }

    /** The lines' quantity in the item's base unit - what the row counts. */
    public BigDecimal baseQuantity() {
        return quantity.multiply(factor);
    }
}
