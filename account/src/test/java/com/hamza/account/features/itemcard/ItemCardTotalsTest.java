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

    /**
     * The net is displayed between the opening and the closing balance, so a movement it
     * leaves out is a contradiction among three figures on one row. Transfers were left
     * out until 2026-09-20 and this is the case that would have caught it.
     */
    @Test
    void netQuantityCountsTransfersBothWays() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.PURCHASE, 10, 1, 0, 0),
                row(ProcessType.TRANSFER_IN, 6, 1, 0, 0),
                row(ProcessType.TRANSFER_OUT, 4, 1, 0, 0)));

        assertEquals(6, totals.transferIn(), PRECISION);
        assertEquals(4, totals.transferOut(), PRECISION);
        assertEquals(12, totals.netQuantity(), PRECISION);
    }

    /** A transfer moves goods between two shelves of one business, at no price. */
    @Test
    void aTransferIsWorthNothingAndEarnsNothing() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.TRANSFER_IN, 6, 1, 0, 0),
                row(ProcessType.TRANSFER_OUT, 4, 1, 0, 0)));

        assertEquals(0, totals.costPurchase(), PRECISION);
        assertEquals(0, totals.costSales(), PRECISION);
        assertEquals(0, totals.profit(), PRECISION);
    }

    /**
     * A count found three more than the system said; another found two fewer. Read as
     * magnitudes and given one direction they would come to five in whichever direction
     * was chosen - which is why the adjustment is summed signed.
     */
    @Test
    void aPostedCountAdjustsInTheDirectionItFound() {
        ItemCardTotals totals = ItemCardTotals.of(List.of(
                row(ProcessType.STOCK_COUNT, 3, 1, 0, 0),
                row(ProcessType.STOCK_COUNT, -2, 1, 0, 0)));

        assertEquals(1, totals.adjustment(), PRECISION);
        assertEquals(1, totals.netQuantity(), PRECISION);
    }

    /**
     * The figure the screen shows between the opening and closing balance has to be the
     * difference between them, whatever kinds of movement the period held.
     */
    @Test
    void theNetIsTheChangeTheRunningBalanceArrivesAt() {
        List<CardItems> rows = List.of(
                row(ProcessType.PURCHASE, 10, 1, 0, 0),
                row(ProcessType.TRANSFER_OUT, 4, 1, 0, 0),
                row(ProcessType.SALES, 3, 1, 0, 0),
                row(ProcessType.STOCK_COUNT, -1, 1, 0, 0));

        double opening = 5;
        double closing = ItemCardRunningBalance.apply(rows, opening);

        assertEquals(closing - opening, ItemCardTotals.of(rows).netQuantity(), PRECISION);
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
     * the movement - a purchase, a sales return and a transfer in are in; a sale, a
     * purchase return and a transfer out are out. A posted count is the exception the
     * view makes: its quantity is a difference and already carries its own sign.
     */
    private static CardItems row(ProcessType processType, double quantity, double factor, double totals, double profit) {
        CardItems row = new CardItems();
        row.setProcessType(processType);
        row.setQuantity(quantity);
        row.setTypeValue(factor);
        boolean incoming = processType == ProcessType.PURCHASE
                || processType == ProcessType.SALES_RETURN
                || processType == ProcessType.TRANSFER_IN;
        row.setBaseQuantity(processType == ProcessType.STOCK_COUNT
                ? quantity * factor
                : (incoming ? 1 : -1) * quantity * factor);
        row.setTotals(totals);
        row.setProfit(profit);
        return row;
    }
}
