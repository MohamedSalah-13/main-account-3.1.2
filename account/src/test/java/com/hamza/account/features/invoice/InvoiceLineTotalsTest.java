package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceLineTotalsTest {

    @Test
    void summarizesRowsWithTheExistingFinancialRoundingRule() {
        var totals = InvoiceLineTotals.from(List.of(
                line(1.005, 10.005, 0.105, 5),
                line(2, 20, 0.20, 10)));

        assertEquals(2, totals.lineCount());
        assertEquals(3.01, totals.quantity());
        assertEquals(30.01, totals.gross());
        assertEquals(0.31, totals.discount());
        assertEquals(29.70, totals.net());
        assertEquals(new BigDecimal("29.70"), totals.netAmount());
        assertFalse(totals.hasInvalidLine());
    }

    @Test
    void keepsMoneyExactWhileAggregatingRows() {
        var totals = InvoiceLineTotals.from(List.of(
                line(1, 0.1, 0, 0.1),
                line(1, 0.2, 0, 0.2)));

        assertEquals(new BigDecimal("0.30"), totals.grossAmount());
        assertEquals(new BigDecimal("0.30"), totals.netAmount());
    }

    @Test
    void reportsInvalidRowsAndHandlesAnEmptyDraft() {
        assertTrue(InvoiceLineTotals.from(List.of(line(0, 0, 0, 0))).hasInvalidLine());
        assertEquals(new InvoiceLineTotals(0, 0, 0, 0, 0, false),
                InvoiceLineTotals.from(List.of()));
    }

    /**
     * The quick screen's trailing entry row. It has to be invisible to every total,
     * because {@code hasInvalidLine} is bound to the save buttons' disable state and a
     * row with no price and no quantity would keep them disabled for ever.
     */
    @Test
    void ignoresTheEntryPlaceholderRow() {
        var totals = InvoiceLineTotals.from(List.of(line(2, 20, 0, 10), placeholder()));

        assertEquals(1, totals.lineCount());
        assertEquals(2, totals.quantity());
        assertEquals(20, totals.gross());
        assertEquals(20, totals.net());
        assertFalse(totals.hasInvalidLine());
    }

    @Test
    void aPlaceholderOnItsOwnIsAnEmptyInvoice() {
        assertEquals(new InvoiceLineTotals(0, 0, 0, 0, 0, false),
                InvoiceLineTotals.from(List.of(placeholder())));
    }

    /** A row that does name an item is still judged, however empty its figures are. */
    @Test
    void stillReportsAnInvalidRowThatNamesAnItem() {
        assertTrue(InvoiceLineTotals.from(List.of(line(0, 0, 0, 0, 7))).hasInvalidLine());
        assertTrue(InvoiceLineTotals.isPlaceholder(placeholder()));
        assertFalse(InvoiceLineTotals.isPlaceholder(line(1, 1, 0, 1, 7)));
    }

    private static Purchase placeholder() {
        Purchase line = new Purchase();
        line.setItems(new ItemsModel());
        return line;
    }

    private static Purchase line(double quantity, double total, double discount, double price) {
        return line(quantity, total, discount, price, 1);
    }

    private static Purchase line(double quantity, double total, double discount,
                                 double price, int itemId) {
        Purchase line = new Purchase();
        line.setQuantity(quantity);
        line.setTotal(total);
        line.setDiscount(discount);
        line.setPrice(price);
        ItemsModel item = new ItemsModel();
        item.setId(itemId);
        line.setItems(item);
        return line;
    }
}
