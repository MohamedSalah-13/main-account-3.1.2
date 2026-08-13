package com.hamza.account.features.invoice;

import com.hamza.account.type.InvoiceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePaymentTermsTest {

    @Test
    void cashAlwaysPaysTheWholeNetAmount() throws Exception {
        InvoicePaymentTerms terms = InvoicePaymentTerms.resolve(
                InvoiceType.CASH, 125.50, 5.50, 1);

        assertEquals(120.0, terms.net());
        assertEquals(120.0, terms.paid());
        assertEquals(0.0, terms.remaining());
        assertFalse(terms.deferred());
    }

    @Test
    void deferredInvoiceSupportsZeroOrPartialInitialPayment() throws Exception {
        InvoicePaymentTerms unpaid = InvoicePaymentTerms.resolve(
                InvoiceType.DEFER, 100, 10, 0);
        InvoicePaymentTerms partial = InvoicePaymentTerms.resolve(
                InvoiceType.DEFER, 100, 10, 25);

        assertEquals(90, unpaid.remaining());
        assertEquals(25, partial.paid());
        assertEquals(65, partial.remaining());
        assertTrue(partial.deferred());
    }

    @Test
    void rejectsInvalidDiscountAndPaymentBoundaries() {
        assertTarget(InvoiceSaveValidator.Target.DISCOUNT,
                () -> InvoicePaymentTerms.resolve(InvoiceType.DEFER, 100, 101, 0));
        assertTarget(InvoiceSaveValidator.Target.DISCOUNT,
                () -> InvoicePaymentTerms.resolve(InvoiceType.DEFER, 100, -1, 0));
        assertTarget(InvoiceSaveValidator.Target.PAID,
                () -> InvoicePaymentTerms.resolve(InvoiceType.DEFER, 100, 0, -1));
        assertTarget(InvoiceSaveValidator.Target.PAID,
                () -> InvoicePaymentTerms.resolve(InvoiceType.DEFER, 100, 0, 101));
    }

    @Test
    void roundsEveryPersistedMoneyValue() throws Exception {
        InvoicePaymentTerms terms = InvoicePaymentTerms.resolve(
                InvoiceType.DEFER, 10.005, 0.004, 3.335);

        assertEquals(10.01, terms.subtotal());
        assertEquals(0.0, terms.discount());
        assertEquals(10.01, terms.net());
        assertEquals(3.34, terms.paid());
        assertEquals(6.67, terms.remaining());
    }

    private void assertTarget(InvoiceSaveValidator.Target target,
                              ThrowingOperation operation) {
        InvoiceValidationException exception = assertThrows(
                InvoiceValidationException.class, operation::run);
        assertEquals(target, exception.target());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
