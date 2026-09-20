package com.hamza.account.features.itemcard;

import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;

import java.util.List;

/**
 * What one item's card adds up to over a period: the quantities in the item's <b>base
 * unit</b>, what each kind of document was worth, and the profit the sales in it made.
 * <p>
 * The quantities are the reason this class exists. They used to be summed by multiplying
 * each line by {@code units.value_d} - one factor for the whole database - so an item
 * bought by a carton of 200 and another bought by a carton of 12 were both counted as
 * whatever the units screen last said a carton was. The factor is per item and per line
 * ({@code items_units.quantity}, stored on the line as {@code type_value}), which is what
 * {@link CardItems#getBaseQuantity()} carries and what {@code quantity_items_table}
 * counts with.
 * <p>
 * <b>The three warehouse movements are here because {@link #netQuantity()} is displayed
 * as the change in the balance</b>, between the opening and closing balances on the same
 * row of the screen. Leaving transfers and posted counts out of it - as this class did
 * until 2026-09-20 - made those three figures contradict each other on any warehouse that
 * had sent, received or been counted, with nothing on the screen to say which was wrong.
 * <p>
 * There is no value or profit for them, and that is not an omission: a transfer moves
 * goods between two shelves of one business at no price, and a count corrects what is on
 * a shelf. Neither is a sale.
 * <p>
 * No JavaFX and no database: the screen hands it rows and shows what comes back.
 */
public record ItemCardTotals(double purchase,
                             double sales,
                             double purchaseReturn,
                             double salesReturn,
                             double transferIn,
                             double transferOut,
                             double adjustment,
                             double costPurchase,
                             double costSales,
                             double costPurchaseReturn,
                             double costSalesReturn,
                             double profit) {

    public static final ItemCardTotals EMPTY =
            new ItemCardTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public static ItemCardTotals of(List<CardItems> rows) {
        return new ItemCardTotals(
                quantity(rows, ProcessType.PURCHASE),
                quantity(rows, ProcessType.SALES),
                quantity(rows, ProcessType.PURCHASE_RETURN),
                quantity(rows, ProcessType.SALES_RETURN),
                quantity(rows, ProcessType.TRANSFER_IN),
                quantity(rows, ProcessType.TRANSFER_OUT),
                signedQuantity(rows, ProcessType.STOCK_COUNT),
                value(rows, ProcessType.PURCHASE),
                value(rows, ProcessType.SALES),
                value(rows, ProcessType.PURCHASE_RETURN),
                value(rows, ProcessType.SALES_RETURN),
                profit(rows));
    }

    /**
     * What the period moved, in base units: what came in less what went out. It is the
     * change in the balance, not the balance - a card that starts with stock on the shelf
     * has an opening balance to add, which only the database can answer.
     * <p>
     * The adjustment is added rather than taken a side of, because it carries its own
     * sign: a count that found more adds and one that found less subtracts, and both are
     * the same kind of movement.
     */
    public double netQuantity() {
        return (purchase + salesReturn + transferIn + adjustment) - (sales + purchaseReturn + transferOut);
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

    /**
     * The magnitude trick above cannot be used for a posted count: its rows go both ways,
     * so reading them as magnitudes and then choosing one direction would report a count
     * that found three missing and a count that found three extra as the same six.
     */
    private static double signedQuantity(List<CardItems> rows, ProcessType processType) {
        return rows.stream()
                .filter(row -> row.getProcessType() == processType)
                .mapToDouble(CardItems::getBaseQuantity)
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
     * the profit its sale made, so it is subtracted. A transfer and a count carry none,
     * and the view says so rather than leaving it to arithmetic over a price of zero.
     */
    private static double profit(List<CardItems> rows) {
        return rows.stream().mapToDouble(row ->
                row.getProcessType() == ProcessType.SALES_RETURN ? -row.getProfit() : row.getProfit()).sum();
    }
}
