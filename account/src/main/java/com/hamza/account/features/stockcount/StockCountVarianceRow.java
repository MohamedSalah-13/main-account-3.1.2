package com.hamza.account.features.stockcount;

/**
 * What the posted counts of a period found about one item, in its base unit.
 * <p>
 * Summed per item and never across items: every figure here is in one item's own base unit, so a
 * total of them is meaningful for the item and meaningless for the report.
 *
 * @param counts   how many posted sheets found this item different from the book
 * @param surplus  what those sheets found over the book, added up - never negative
 * @param shortage what they found under it, added up and written as a positive figure
 * @param net      surplus less shortage - what the counts moved the item's balances by
 */
public record StockCountVarianceRow(int itemId, String code, String itemName, String unitName, int counts,
                                    double surplus, double shortage, double net) {
}
