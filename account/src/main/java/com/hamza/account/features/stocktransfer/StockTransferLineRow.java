package com.hamza.account.features.stocktransfer;

/**
 * One line of a posted transfer, as it was entered: the item, the unit it was counted in and how
 * many of that unit. What the history's detail panel lists and what the slip prints.
 * <p>
 * The quantity is the one typed, never converted: a slip that says "24 pieces" where the
 * storekeeper wrote "2 cartons" is a slip nobody can check against the boxes on the van.
 *
 * @param code the item's own barcode, the code printed on its packet
 */
public record StockTransferLineRow(int itemId, String code, String itemName, String unitName, double quantity) {
}
