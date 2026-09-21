package com.hamza.account.features.stockopening;

/**
 * One item in one warehouse, as the opening-balances screen lists it.
 *
 * @param opening  {@code items_stock.first_balance}, in the item's base unit
 * @param moved    whether anything has moved the item in this warehouse - an invoice line, a
 *                 return, either half of a transfer, a count line - after which its opening is a
 *                 closed entry ({@code WarehouseOpeningBalance})
 */
public record WarehouseOpeningRow(int itemId, String code, String name, String unitName, double opening,
                                  boolean moved) {
}
