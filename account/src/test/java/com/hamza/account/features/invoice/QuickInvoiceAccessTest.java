package com.hamza.account.features.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickInvoiceAccessTest {

    @Test
    void theQuickScreenExistsForANewSaleAndANewPurchaseOnly() {
        // A return is written against a source invoice through a picker the entry row has no
        // room for, so it has no quick screen whatever the user holds.
        Set<Object> everything = Set.of(AppPermissions.SALES_QUICK, AppPermissions.PURCHASE_QUICK);

        assertTrue(QuickInvoiceAccess.allowed(DocumentType.SALES, everything::contains));
        assertTrue(QuickInvoiceAccess.allowed(DocumentType.PURCHASE, everything::contains));
        assertFalse(QuickInvoiceAccess.allowed(DocumentType.SALES_RETURN, key -> true));
        assertFalse(QuickInvoiceAccess.allowed(DocumentType.PURCHASE_RETURN, key -> true));
    }

    @Test
    void eachDocumentAsksItsOwnKey() {
        assertTrue(QuickInvoiceAccess.allowed(DocumentType.SALES, AppPermissions.SALES_QUICK::equals));
        assertFalse(QuickInvoiceAccess.allowed(DocumentType.PURCHASE, AppPermissions.SALES_QUICK::equals));
        assertFalse(QuickInvoiceAccess.allowed(DocumentType.SALES, AppPermissions.SALES_CREATE::equals));
    }

    @Test
    void theKeysAreTheOnesDocumentTypeDeclares() {
        assertEquals(AppPermissions.SALES_QUICK, DocumentType.SALES.quickEntryPermission().orElseThrow());
        assertEquals(AppPermissions.PURCHASE_QUICK, DocumentType.PURCHASE.quickEntryPermission().orElseThrow());
        assertEquals("sales.quick", AppPermissions.SALES_QUICK.value());
        assertEquals("purchase.quick", AppPermissions.PURCHASE_QUICK.value());
    }

    @Test
    void aDocumentsOwnChoiceWins() {
        assertTrue(QuickInvoiceAccess.opensQuick("QUICK", "STANDARD", true));
        assertFalse(QuickInvoiceAccess.opensQuick("STANDARD", "QUICK", true));
    }

    @Test
    void theChoiceMadeBeforeItWasPerDocumentStillCounts() {
        // Somebody who switched to the quick screen on 4.10 keeps it after the upgrade.
        assertTrue(QuickInvoiceAccess.opensQuick("", "QUICK", true));
        assertTrue(QuickInvoiceAccess.opensQuick(null, " QUICK ", true));
        assertFalse(QuickInvoiceAccess.opensQuick("", "", true));
    }

    @Test
    void aUserWithoutTheKeyAlwaysOpensTheStandardScreen() {
        // The remembered choice is the computer's, and the next person at it may not hold the key.
        assertFalse(QuickInvoiceAccess.opensQuick("QUICK", "QUICK", false));
    }

    @Test
    void anUnreadableChoiceIsTheStandardScreen() {
        // A preference must never be the reason a screen fails to open.
        assertFalse(QuickInvoiceAccess.opensQuick("FAST", "", true));
    }
}
