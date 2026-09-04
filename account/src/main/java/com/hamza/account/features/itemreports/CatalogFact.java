package com.hamza.account.features.itemreports;

import java.time.LocalDate;

/**
 * Everything the reports in this package need to know about one item, read in one pass.
 * <p>
 * <b>This record is what makes the reports testable.</b> Each report is a pure function
 * from a list of these to an {@link ItemReportResult} - no connection, no DAO, no JavaFX -
 * so what a report <em>says</em> is checkable by a plain unit test with a handful of
 * hand-built facts, and only the reading of them needs a database. That split is why a
 * fifth report costs one class and one test rather than a query nobody can exercise.
 * <p>
 * It is also why the reports are fast. One query over the catalogue answers all of them;
 * a report that went back to the database per row would turn a four-thousand-item
 * catalogue into four thousand round trips, which is exactly what {@code ItemsDao.map} did
 * to the items list before {@code mapCatalogRow} was written.
 *
 * @param balance      what is on hand, computed by {@code ItemCatalogSql.BALANCE} - the same
 *                     arithmetic the items screen displays, so a report and the screen it
 *                     was opened from can never state two different stocks for one item
 * @param minimum      {@code items.mini_quantity}. Zero means no minimum is set, not that
 *                     everything is low - the rule {@code StockLevel.of} already applies.
 * @param lastMovement the date of the most recent document this item appeared on, across
 *                     all four document families, or {@code null} if it has never appeared
 *                     on one at all
 */
public record CatalogFact(int id,
                          String barcode,
                          String name,
                          Integer mainGroupId,
                          String mainGroupName,
                          Integer subGroupId,
                          String subGroupName,
                          String unitName,
                          double buyPrice,
                          double sellPrice,
                          double minimum,
                          double balance,
                          boolean active,
                          boolean tracksExpiry,
                          LocalDate lastMovement) {

    /** What the stock on hand cost to buy. The figure an owner means by "money on the shelf". */
    public double valueAtCost() {
        return buyPrice * balance;
    }

    /** What the stock on hand would fetch at the first price tier. */
    public double valueAtSale() {
        return sellPrice * balance;
    }

    /** The profit the stock on hand would earn if it all sold at the first tier. */
    public double potentialProfit() {
        return valueAtSale() - valueAtCost();
    }

    /**
     * The margin as a percentage of the sale price, or {@code 0} where there is no sale
     * price to take a percentage of - which is itself the anomaly the price report looks
     * for, and must not be a division by zero on the way to reporting it.
     */
    public double marginPercent() {
        return sellPrice == 0 ? 0 : ((sellPrice - buyPrice) / sellPrice) * 100;
    }

    /** True when nothing has ever been bought or sold against this item. */
    public boolean neverMoved() {
        return lastMovement == null;
    }

    public boolean hasBarcode() {
        return barcode != null && !barcode.isBlank();
    }
}
