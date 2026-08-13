package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePaymentViewModelTest {

    @Test
    void cashDerivesPaidAndRemainingFromTheNetAmount() {
        InvoicePaymentViewModel model = new InvoicePaymentViewModel();

        model.selectInvoiceType(InvoiceType.CASH, false);
        model.updateAmounts(100, 10, 3);

        assertTrue(model.isValid());
        assertEquals(90, model.preview().net());
        assertEquals(90, model.preview().paid());
        assertEquals(0, model.preview().remaining());
    }

    @Test
    void selectingDeferredCanResetAFormerAdvancePayment() {
        InvoicePaymentViewModel model = new InvoicePaymentViewModel();
        model.selectInvoiceType(InvoiceType.DEFER, false);
        model.updateAmounts(100, 0, 30);
        assertEquals(30, model.preview().paid());

        model.selectInvoiceType(InvoiceType.DEFER, true);

        assertEquals(0, model.preview().paid());
        assertEquals(100, model.preview().remaining());
    }

    @Test
    void exposesTheFieldResponsibleForAnInvalidDraft() {
        InvoicePaymentViewModel model = new InvoicePaymentViewModel();
        model.selectInvoiceType(InvoiceType.DEFER, false);

        model.updateAmounts(100, 101, 0);
        assertFalse(model.isValid());
        assertEquals(InvoiceSaveValidator.Target.DISCOUNT, model.invalidTarget());

        model.updateAmounts(100, 0, 101);
        assertFalse(model.isValid());
        assertEquals(InvoiceSaveValidator.Target.PAID, model.invalidTarget());
    }
}
