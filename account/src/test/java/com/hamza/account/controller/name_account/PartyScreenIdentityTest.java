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

    @Test
    void formProfileUsesTheSameIdentityForAddAndEdit() {
        for (PartyKind kind : PartyKind.values()) {
            PartyScreenIdentity identity = PartyScreenIdentity.forKind(kind);
            PartyFormProfile add = identity.formProfile(false);
            PartyFormProfile edit = identity.formProfile(true);

            assertEquals(identity.styleClass(), add.styleClass());
            assertEquals(identity.icon(), add.icon());
            assertFalse(add.title().isBlank());
            assertFalse(add.subtitle().isBlank());
            assertFalse(edit.title().isBlank());
            assertNotEquals(add.title(), edit.title());
        }
    }

    @Test
    void accountScreensWearTheIdentityOfTheirParty() {
        PartyScreenIdentity customer = PartyScreenIdentity.forKind(PartyKind.CUSTOMER);
        PartyScreenIdentity supplier = PartyScreenIdentity.forKind(PartyKind.SUPPLIER);

        for (PartyScreenIdentity identity : new PartyScreenIdentity[]{customer, supplier}) {
            for (PartyFormProfile profile : new PartyFormProfile[]{
                    identity.balancesProfile(), identity.paymentProfile(), identity.trendProfile(),
                    identity.ageingProfile()}) {
                assertEquals(identity.styleClass(), profile.styleClass());
                assertEquals(identity.icon(), profile.icon());
                assertFalse(profile.title().isBlank());
                assertFalse(profile.subtitle().isBlank());
            }
        }
        assertNotEquals(customer.balancesProfile().title(), supplier.balancesProfile().title());
        assertNotEquals(customer.paymentProfile().title(), supplier.paymentProfile().title());
        assertNotEquals(customer.trendProfile().title(), supplier.trendProfile().title());
        assertNotEquals(customer.ageingProfile().title(), supplier.ageingProfile().title());
    }
}
