package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceDetailsPresentationTest {

    @Test
    void exposesOnlyCapabilitiesThatBelongToTheDocumentType() {
        var sale = InvoiceDetailsPresentation.of(DocumentType.SALES, true);
        var saleReturn = InvoiceDetailsPresentation.of(DocumentType.SALES_RETURN, true);
        var purchase = InvoiceDetailsPresentation.of(DocumentType.PURCHASE, true);
        var purchaseReturn = InvoiceDetailsPresentation.of(DocumentType.PURCHASE_RETURN, true);

        assertTrue(sale.showProfit());
        assertTrue(sale.showDelegate());
        assertFalse(sale.showReturnDetails());

        assertTrue(saleReturn.showProfit());
        assertTrue(saleReturn.showDelegate());
        assertTrue(saleReturn.showReturnDetails());

        assertFalse(purchase.showProfit());
        assertFalse(purchase.showDelegate());
        assertFalse(purchase.showReturnDetails());

        assertFalse(purchaseReturn.showProfit());
        assertFalse(purchaseReturn.showDelegate());
        assertTrue(purchaseReturn.showReturnDetails());
    }

    @Test
    void profitPermissionCannotExposePurchaseCostsOrProfit() {
        assertFalse(InvoiceDetailsPresentation.of(DocumentType.SALES, false).showProfit());
        assertFalse(InvoiceDetailsPresentation.of(DocumentType.PURCHASE, true).showProfit());
    }

    @Test
    void assignsAStableSemanticStyleToEveryDocumentType() {
        assertEquals("invoice-details-sales",
                InvoiceDetailsPresentation.of(DocumentType.SALES, false).styleClass());
        assertEquals("invoice-details-sales-return",
                InvoiceDetailsPresentation.of(DocumentType.SALES_RETURN, false).styleClass());
        assertEquals("invoice-details-purchase",
                InvoiceDetailsPresentation.of(DocumentType.PURCHASE, false).styleClass());
        assertEquals("invoice-details-purchase-return",
                InvoiceDetailsPresentation.of(DocumentType.PURCHASE_RETURN, false).styleClass());
    }
}
