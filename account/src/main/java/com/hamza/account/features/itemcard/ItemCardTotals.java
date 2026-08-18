package com.hamza.account.features.itemcard;

import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;

import java.util.List;

/**
 * What one item's card adds up to over a period: the four quantities in the item's
 * <b>base unit</b>, what each kind of document was worth, and the profit the sales in
 * it made.
 * <p>
 * The quantities are the reason this class exists. They used to be summed by
 * multiplying each line by {@code units.value_d} - one factor for the whole database -
 * so an item bought by a carton of 200 and another bought by a carton of 12 were both
 * counted as whatever the units screen last said a carton was. The factor is per item
 * and per line ({@code items_units.quantity}, stored on the line as {@code type_value}),
 * which is what {@link CardItems#getBaseQuantity()} carries and what
 * {@code quantity_items_table} counts with.
 * <p>
 * No JavaFX and no database: the screen hands it rows and shows what comes back.
 */
public record ItemCardTotals(double purchase,
                             double sales,
                             double purchaseReturn,
                             double salesReturn,
                             double costPurchase,
                             double costSales,
                             double costPurchaseReturn,
                             double costSalesReturn,
                             double profit) {

    public static final ItemCardTotals EMPTY = new ItemCardTotals(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static ItemCardTotals of(List<CardItems> rows) {
        return new ItemCardTotals(
                quantity(rows, ProcessType.PURCHASE),
                quantity(rows, ProcessType.SALES),
                quantity(rows, ProcessType.PURCHASE_RETURN),
                quantity(rows, ProcessType.SALES_RETURN),
                value(rows, ProcessType.PURCHASE),
                value(rows, ProcessType.SALES),
                value(rows, ProcessType.PURCHASE_RETURN),
                value(rows, ProcessType.SALES_RETURN),
                profit(rows));
    }

    /**
     * What the period moved, in base units: what came in less what went out. It is the
     * change in the balance, not the balance - a card that starts with stock on the
     * shelf has an opening balance to add, which only the database can answer.
     */
    public double netQuantity() {
        return (purchase + salesReturn) - (sales + purchaseReturn);
    }

    private static double quantity(List<CardItems> rows, ProcessType processType) {
        return rows.stream()
                .filter(row -> row.getProcessType() == processType)
                // The signed base quantity, read as a magnitude: the sign is the
                // direction of the movement and is applied by netQuantity, so adding
                // it here would subtract the sales twice.
                .mapToDouble(row -> Math.abs(row.getBaseQuantity()))
                .sum();
    }

    private static double value(List<CardItems> rows, ProcessType processType) {
        return rows.stream()
                .filter(row -> row.getProcessType() == processType)
                .mapToDouble(CardItems::getTotals)
                .sum();
    }

    /**
     * Only the sales sides carry a profit - a purchase has no cost to compare against,
     * and the view leaves its {@code profit} at zero - and a sales return gives back
     * the profit its sale made, so it is subtracted.
     */
    private static double profit(List<CardItems> rows) {
        return rows.stream().mapToDouble(row ->
                row.getProcessType() == ProcessType.SALES_RETURN ? -row.getProfit() : row.getProfit()).sum();
    }
}
