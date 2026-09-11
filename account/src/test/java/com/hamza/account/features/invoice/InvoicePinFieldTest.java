package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePinFieldTest {

    @Test
    void keysKeepSalesAndPurchasesApart() {
        assertEquals("invoice.pin.sales.treasury", InvoicePinField.TREASURY.preferenceKey(DocumentType.SALES));
        assertEquals("invoice.pin.purchase.treasury", InvoicePinField.TREASURY.preferenceKey(DocumentType.PURCHASE));
        assertEquals("invoice.pin.sales_return.stock", InvoicePinField.STOCK.preferenceKey(DocumentType.SALES_RETURN));
    }

    @Test
    void delegateExistsOnlyOnSalesDocuments() {
        assertTrue(InvoicePinField.DELEGATE.appliesTo(DocumentType.SALES));
        assertTrue(InvoicePinField.DELEGATE.appliesTo(DocumentType.SALES_RETURN));
        assertFalse(InvoicePinField.DELEGATE.appliesTo(DocumentType.PURCHASE));
        assertFalse(InvoicePinField.DELEGATE.appliesTo(DocumentType.PURCHASE_RETURN));
    }

    @Test
    void everyOtherHeaderChoiceAppliesToEveryDocument() {
        for (DocumentType type : DocumentType.values()) {
            assertTrue(InvoicePinField.PARTY.appliesTo(type));
            assertTrue(InvoicePinField.TREASURY.appliesTo(type));
            assertTrue(InvoicePinField.STOCK.appliesTo(type));
        }
    }
}
