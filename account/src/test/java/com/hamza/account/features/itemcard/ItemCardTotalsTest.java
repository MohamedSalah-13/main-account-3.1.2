package com.hamza.account.features.itemcard;

import com.hamza.account.model.domain.CardItems;
import com.hamza.account.type.ProcessType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCardTotalsTest {

    private static final double PRECISION = 0.0001;

    /**
     * Two cartons of twelve and one of two hundred are 224 pieces, not three cartons
     * of whatever the units screen happens to call a carton. The factor comes from the
     * line, which is what the card was getting wrong.
     */
    @Test
    void countsEveryLineWithTheFactorTheLineStored() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.PURCHASE, 2, 12, 0, 0),
                row(ProcessType.PURCHASE, 1, 200, 0, 0)));

        assertEquals(224, totals.purchase(), PRECISION);
    }

    @Test
    void netQuantityIsWhatCameInLessWhatWentOut() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.PURCHASE, 10, 1, 0, 0),
                row(ProcessType.SALES, 4, 1, 0, 0),
                row(ProcessType.SALES_RETURN, 1, 1, 0, 0),
                row(ProcessType.PURCHASE_RETURN, 2, 1, 0, 0)));

        assertEquals(10, totals.purchase(), PRECISION);
        assertEquals(4, totals.sales(), PRECISION);
        assertEquals(1, totals.salesReturn(), PRECISION);
        assertEquals(2, totals.purchaseReturn(), PRECISION);
        assertEquals(5, totals.netQuantity(), PRECISION);
    }

    /** A movement out is stored signed; a quantity total is a magnitude. */
    @Test
    void aSaleCountsTowardsItsOwnTotalAsAPositiveQuantity() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(row(ProcessType.SALES, 3, 12, 0, 0)));

        assertEquals(36, totals.sales(), PRECISION);
        assertEquals(-36, totals.netQuantity(), PRECISION);
    }

    @Test
    void valuesEachKindOfDocumentSeparately() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.PURCHASE, 1, 1, 100, 0),
                row(ProcessType.SALES, 1, 1, 150, 0),
                row(ProcessType.SALES_RETURN, 1, 1, 50, 0),
                row(ProcessType.PURCHASE_RETURN, 1, 1, 30, 0)));

        assertEquals(100, totals.costPurchase(), PRECISION);
        assertEquals(150, totals.costSales(), PRECISION);
        assertEquals(50, totals.costSalesReturn(), PRECISION);
        assertEquals(30, totals.costPurchaseReturn(), PRECISION);
    }

    /** A return gives back the profit its sale made. */
    @Test
    void aSalesReturnTakesBackItsProfit() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.SALES, 1, 1, 0, 40),
                row(ProcessType.SALES_RETURN, 1, 1, 0, 15),
                row(ProcessType.PURCHASE, 1, 1, 0, 0)));

        assertEquals(25, totals.profit(), PRECISION);
    }

    @Test
    void anEmptyPeriodTotalsNothing() {
        assertEquals(0, ItemCardTotals.of(List.of()).netQuantity(), PRECISION);
    }

    @Test
    void theRunningBalanceStartsAtTheOpeningBalanceAndFollowsTheMovements() {
        List<CardItems> rows = List.of(
                row(ProcessType.PURCHASE, 10, 1, 0, 0),
                row(ProcessType.SALES, 4, 1, 0, 0),
                row(ProcessType.SALES_RETURN, 1, 1, 0, 0));

        double closing = ItemCardRunningBalance.apply(rows, 5);

        assertEquals(15, rows.get(0).getBalance(), PRECISION);
        assertEquals(11, rows.get(1).getBalance(), PRECISION);
        assertEquals(12, rows.get(2).getBalance(), PRECISION);
        assertEquals(12, closing, PRECISION);
    }

    /**
     * A row carries the base quantity the view computed, signed by the direction of
     * the movement - a purchase and a sales return in, a sale and a purchase return
     * out.
     */
    private static CardItems row(ProcessType processType, double quantity, double factor, double totals, double profit) {
        CardItems row = new CardItems();
        row.setProcessType(processType);
        row.setQuantity(quantity);
        row.setTypeValue(factor);
        boolean incoming = processType == ProcessType.PURCHASE || processType == ProcessType.SALES_RETURN;
        row.setBaseQuantity((incoming ? 1 : -1) * quantity * factor);
        row.setTotals(totals);
        row.setProfit(profit);
        return row;
    }
}
