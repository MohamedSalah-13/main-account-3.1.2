package com.hamza.account.features.invoice;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceEditorViewModelTest {

    @Test
    void keepsTotalsSynchronizedWithLineAndPropertyChanges() {
        InvoiceEditorViewModel<Purchase> editor = new InvoiceEditorViewModel<>();
        Purchase line = line(2, 10, 1, 5);

        editor.lines().add(line);
        assertEquals(new BigDecimal("9.00"), editor.totals().netAmount());
        assertFalse(editor.invalidLinesProperty().get());

        line.setTotal(12);
        line.setDiscount(2);
        assertEquals(new BigDecimal("10.00"), editor.totals().netAmount());

        line.setPrice(0);
        assertTrue(editor.invalidLinesProperty().get());
    }

    @Test
    void replacesLinesAndOwnsTheCurrentItemAndSavingState() {
        InvoiceEditorViewModel<Purchase> editor = new InvoiceEditorViewModel<>();
        ItemsModel item = new ItemsModel();
        item.setId(7);

        editor.replaceLines(List.of(line(1, 3, 0, 3), line(2, 8, 1, 4)));
        editor.selectItem(item);
        editor.setSaving(true);

        assertEquals(2, editor.totals().lineCount());
        assertEquals(new BigDecimal("10.00"), editor.totals().netAmount());
        assertSame(item, editor.selectedItem());
        assertTrue(editor.isSaving());
    }

    @Test
    void derivesPaymentFromTheCurrentLineSummary() throws Exception {
        InvoiceEditorViewModel<Purchase> editor = new InvoiceEditorViewModel<>();
        editor.lines().add(line(1, 100, 0, 100));

        InvoicePaymentTerms preview = editor.updatePayment(
                InvoiceType.DEFER, false, new BigDecimal("10"), new BigDecimal("25"));

        assertEquals(new BigDecimal("90.00"), preview.netAmount());
        assertEquals(new BigDecimal("25.00"), preview.paidAmount());
        assertEquals(new BigDecimal("65.00"), preview.remainingAmount());
        assertEquals(preview, editor.requireValidPayment());
        assertTrue(editor.isPaymentValid());
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
