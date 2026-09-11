package com.hamza.account.controller.name_account;

import com.hamza.account.config.AppIcon;
import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyScreenIdentityTest {

    @Test
    void customerAndSupplierHaveDistinctReusableIdentities() {
        PartyScreenIdentity customer = PartyScreenIdentity.forKind(PartyKind.CUSTOMER);
        PartyScreenIdentity supplier = PartyScreenIdentity.forKind(PartyKind.SUPPLIER);

        assertEquals("party-customers", customer.styleClass());
        assertEquals(AppIcon.CUSTOMERS, customer.icon());
        assertEquals("party-suppliers", supplier.styleClass());
        assertEquals(AppIcon.SUPPLIERS, supplier.icon());
        assertNotEquals(customer.styleClass(), supplier.styleClass());
        assertNotEquals(customer.icon(), supplier.icon());
    }

    @Test
    void listProfileKeepsRecordActionsInTheirRows() {
        for (PartyKind kind : PartyKind.values()) {
            var profile = PartyScreenIdentity.forKind(kind).listProfile();

            assertTrue(profile.headerVisible());
            assertFalse(profile.updateVisible());
            assertFalse(profile.deleteVisible());
            assertTrue(profile.selectionVisible());
            assertFalse(profile.title().isBlank());
            assertFalse(profile.subtitle().isBlank());
            assertFalse(profile.addButtonText().isBlank());
            assertFalse(profile.searchPrompt().isBlank());
        }
    }
}
