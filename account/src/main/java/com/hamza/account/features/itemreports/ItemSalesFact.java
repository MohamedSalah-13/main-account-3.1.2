package com.hamza.account.features.itemreports;

/**
 * What one item sold over a period, net of the returns dated in it.
 *
 * <p>Base units, and a line's amount after its own discount and before the document's - the
 * {@code ItemNetLines} rule: a discount on a whole invoice belongs to no line. The cost is what the
 * lines recorded ({@code total_buy_price}), which is what {@code document_profit} subtracts, so the
 * items' margins less the invoices' own discounts are that view's profit for the period.</p>
 *
 * @param groupName the item's sub group, else its main group, else {@code null} - the report says "no group"
 * @param quantity base units sold less base units returned
 * @param net      the lines' amounts after their own discounts, less the returned lines'
 * @param cost     the lines' recorded cost, less the returned lines'
 */
public record ItemSalesFact(int itemId, String name, String groupName, String unitName,
                            double quantity, double net, double cost) {

    /** What the item earned before the invoices' own discounts. */
    public double margin() {
        return net - cost;
    }
}
