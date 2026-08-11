package com.hamza.account.features.inventory;

import com.hamza.account.features.notification.StockLevel;
import com.hamza.controlsfx.util.NumberUtils;

/**
 * One line of the inventory sheet: an item, its movements and what is left.
 * <p>
 * This is deliberately not {@code ItemsModel}. The screen used to page over full
 * item models, and loading one of those costs five extra queries per row - the sub
 * group, the unit, the item's other units, its extra barcodes, its stock - plus the
 * item's image, a {@code LONGBLOB}, on a screen that never shows a picture. Fifty
 * rows meant a couple of hundred round trips and however many megabytes of images
 * the shop happens to have, all on the JavaFX thread. Nothing here needs any of it.
 * <p>
 * Quantities are in the item's base unit, which is what {@code quantity_items_table}
 * produces: it multiplies each invoice line by the factor the line was written with
 * ({@code quantity * type_value}), so a carton of twelve and a loose piece are
 * already comparable by the time they arrive here. {@link #unitName} is the base
 * unit's name, so the sheet can say which unit the numbers are counted in instead
 * of leaving the reader to guess.
 */
public record InventoryRow(
        int itemId,
        String nameItem,
        String barcode,
        String unitName,
        boolean active,
        double opening,
        double purchase,
        double sales,
        double purchaseReturn,
        double salesReturn,
        double transferOut,
        double transferIn,
        /** What posted stock counts corrected this item's balance by, signed. */
        double adjustment,
        double balance,
        double buyPrice,
        double sellPrice,
        double miniQuantity) {

    /** What this item's remaining stock cost to buy. */
    public double valueAtCost() {
        return NumberUtils.roundToTwoDecimalPlaces(balance * buyPrice);
    }

    /** What it would bring in at the first sale price. */
    public double valueAtSale() {
        return NumberUtils.roundToTwoDecimalPlaces(balance * sellPrice);
    }

    /**
     * Where this row sits against the item's minimum, decided by the same function
     * the sales screens use so the sheet and the low-stock alert cannot disagree
     * about what "low" means. A minimum of zero means none was set.
     */
    public StockLevel level() {
        return StockLevel.of(balance, miniQuantity);
    }

    // ---------------------------------------------------------------------
    // JasperReports reads its data source by JavaBean getter, and the fields in
    // reports/ar/items-inventory-A4.jrxml are named after ItemsModel's properties.
    // These keep the existing template working unchanged; the report is not worth
    // rewriting to save fifteen lines.
    // ---------------------------------------------------------------------

    public String getNameItem() {
        return nameItem;
    }

    public String getBarcode() {
        return barcode;
    }

    public double getFirstBalanceForStock() {
        return opening;
    }

    public double getSumPurchase() {
        return purchase;
    }

    public double getSumSales() {
        return sales;
    }

    public double getSumPurchaseRe() {
        return purchaseReturn;
    }

    public double getSumSalesRe() {
        return salesReturn;
    }

    public double getFromStock() {
        return transferOut;
    }

    public double getToStock() {
        return transferIn;
    }

    public double getSumAllBalance() {
        return balance;
    }
}
