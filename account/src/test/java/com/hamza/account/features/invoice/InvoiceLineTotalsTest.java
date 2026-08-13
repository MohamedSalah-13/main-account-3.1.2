package com.hamza.account.features.invoice;

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

    private static Purchase line(double quantity, double total, double discount, double price) {
        Purchase line = new Purchase();
        line.setQuantity(quantity);
        line.setTotal(total);
        line.setDiscount(discount);
        line.setPrice(price);
        return line;
    }
}
