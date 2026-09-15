package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceDetailsSummaryTest {

    @Test
    void calculatesHeaderAndLineFiguresWithoutMixingTheirDiscounts() {
        Total_Sales header = new Total_Sales();
        header.setTotal(145);
        header.setDiscount(5);
        header.setPaid(100);

        InvoiceDetailsSummary summary = InvoiceDetailsSummary.from(header, List.of(
                line(1, 2, 100, 10, 60),
                line(1, 1, 50, 0, 30)), true);

        assertEquals(2, summary.lineCount());
        assertEquals(1, summary.distinctItemCount());
        assertDecimal("3", summary.quantity());
        assertDecimal("150", summary.linesTotal());
        assertDecimal("10", summary.linesDiscount());
        assertDecimal("140", summary.linesNet());
        assertDecimal("5", summary.invoiceDiscount());
        assertDecimal("140", summary.invoiceNet());
        assertDecimal("100", summary.paid());
        assertDecimal("40", summary.remaining());
        assertDecimal("90", summary.totalCost().orElseThrow());
        assertDecimal("50", summary.profit().orElseThrow());
    }

    @Test
    void doesNotCalculateSensitiveFiguresWhenProfitIsNotVisible() {
        Total_Sales header = new Total_Sales();

        InvoiceDetailsSummary summary = InvoiceDetailsSummary.from(header,
                List.of(line(1, 1, 10, 0, 6)), false);

        assertTrue(summary.totalCost().isEmpty());
        assertTrue(summary.profit().isEmpty());
    }

    private static Sales line(int itemId, double quantity, double total,
                              double discount, double cost) {
        Sales line = new Sales();
        line.setItems(new ItemsModel(itemId, "Item " + itemId));
        line.setQuantity(quantity);
        line.setTotal(total);
        line.setDiscount(discount);
        line.setTotal_buy_price(cost);
        return line;
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
