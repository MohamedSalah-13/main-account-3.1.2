package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.Sales;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A bundle's barcode puts every component on the invoice or none (V87): what a component folded into, what
 * was added after, and what the offers rewrote while a question was open all go back.
 */
class InvoiceLinesCheckpointTest {

    private static Sales line(double quantity, double price, double discount) {
        Sales line = new Sales();
        line.setQuantity(quantity);
        line.setPrice(price);
        line.setDiscount(discount);
        InvoiceLineService.recalculate(line);
        return line;
    }

    @Test
    void aLineAddedSinceIsTakenOutAndAMergedOneGetsItsQuantityBack() {
        Sales tea = line(2, 30, 0);
        Sales sugar = line(1, 20, 5);
        List<BasePurchasesAndSales> lines = new ArrayList<>(List.of(tea, sugar));
        InvoiceLinesCheckpoint before = InvoiceLinesCheckpoint.of(lines);

        // The first component folds into the tea already on the invoice, the second is a line of its own.
        tea.setQuantity(3);
        InvoiceLineService.recalculate(tea);
        lines.add(line(1, 15, 0));

        before.restore(lines);

        assertEquals(2, lines.size());
        assertSame(tea, lines.get(0));
        assertSame(sugar, lines.get(1));
        assertEquals(2, tea.getQuantity());
        assertEquals(60, tea.getTotal());
        assertEquals(60, tea.getTotal_after_discount());
        assertEquals(15, sugar.getTotal_after_discount());
    }

    @Test
    void whatTheOffersRewroteWhileAQuestionWasOpenIsPutBack() {
        Sales tea = line(2, 30, 0);
        List<BasePurchasesAndSales> lines = new ArrayList<>(List.of(tea));
        InvoiceLinesCheckpoint before = InvoiceLinesCheckpoint.of(lines);

        tea.setDiscount(6);
        tea.setTotal_after_discount(54);
        tea.setOfferId(7);
        tea.setOfferDiscount(new BigDecimal("6.00"));
        tea.setOfferQuantity(new BigDecimal("2"));
        tea.setOfferName("10%");

        before.restore(lines);

        assertEquals(0, tea.getDiscount());
        assertEquals(60, tea.getTotal_after_discount());
        assertNull(tea.getOfferId());
        assertEquals(0, tea.getOfferDiscount().signum());
        assertEquals(0, tea.getOfferQuantity().signum());
        assertNull(tea.getOfferName());
    }

    @Test
    void twoNewLinesOfOneItemAreBothTakenOutWhateverTheyEqual() {
        ObservableList<Sales> lines = FXCollections.observableArrayList();
        InvoiceLinesCheckpoint before = InvoiceLinesCheckpoint.of(lines);
        lines.add(line(1, 10, 0));
        lines.add(line(1, 10, 0));

        before.restore(lines);

        assertEquals(0, lines.size());
    }
}
