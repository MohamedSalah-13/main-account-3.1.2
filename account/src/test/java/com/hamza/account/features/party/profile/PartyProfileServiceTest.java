package com.hamza.account.features.party.profile;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyProfileServiceTest {

    private static final PartyProfileFilter MARCH = new PartyProfileFilter(PartyKind.CUSTOMER, 7,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    private static void signIn(PermissionKey... permissions) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "tester", List.of(permissions));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static PermissionKey showCustomers() {
        return PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showNames();
    }

    @Test
    void nothingIsReadForSomeoneWhoMayNotOpenTheParty() {
        signIn();
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class, () -> new PartyProfileService(repository, (k, p) -> BigDecimal.TEN).profile(MARCH));
        assertTrue(repository.calls.isEmpty());
    }

    /**
     * Opening the party is enough to look - the window this replaced asked no more - and the period
     * before is read too, since "stopped buying" is measured against it.
     */
    @Test
    void viewingAsksWhatOpeningThePartyAsks() throws Exception {
        signIn(showCustomers());
        Recording repository = new Recording();

        new PartyProfileService(repository, (k, p) -> BigDecimal.TEN).profile(MARCH);

        assertEquals(List.of("items 2026-03-01..2026-03-31", "items 2026-01-29..2026-02-28",
                "days 2026-03-01..2026-03-31", "last"), repository.calls);
    }

    @Test
    void aFileAsksTheReportKeyOnTop() throws Exception {
        signIn(showCustomers());
        Recording repository = new Recording();
        PartyProfileService service = new PartyProfileService(repository, (k, p) -> BigDecimal.TEN);

        assertThrows(BusinessRuleException.class, () -> service.forExport(MARCH));
        assertTrue(repository.calls.isEmpty());

        signIn(showCustomers(), AppPermissions.REPORTS_SHOW_CUSTOMERS);
        service.forExport(MARCH);
        assertEquals(4, repository.calls.size());
    }

    @Test
    void theSupplierSideAsksTheSupplierKeys() {
        signIn(showCustomers(), AppPermissions.REPORTS_SHOW_CUSTOMERS);
        PartyProfileFilter supplier = new PartyProfileFilter(PartyKind.SUPPLIER, 3,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThrows(BusinessRuleException.class,
                () -> new PartyProfileService(new Recording(), (k, p) -> BigDecimal.TEN).profile(supplier));
        assertEquals(AppPermissions.REPORTS_SHOW_SUPPLIERS, PartyProfileService.exportPermission(PartyKind.SUPPLIER));
    }

    /** The balance is the statement's, and only for a reader who may see accounts - absent otherwise. */
    @Test
    void theBalanceIsReadOnlyForAReaderWhoMaySeeAccounts() throws Exception {
        List<String> asked = new ArrayList<>();
        PartyProfileService.BalanceReader balances = (kind, party) -> {
            asked.add(kind + " " + party);
            return new BigDecimal("1050.00");
        };

        signIn(showCustomers());
        assertEquals(Optional.empty(), new PartyProfileService(new Recording(), balances).profile(MARCH).balance());
        assertTrue(asked.isEmpty());

        signIn(showCustomers(), PermAccountAndNameInt.forParty(PartyKind.CUSTOMER).showAccounts());
        assertEquals(Optional.of(new BigDecimal("1050.00")),
                new PartyProfileService(new Recording(), balances).profile(MARCH).balance());
        assertEquals(List.of("CUSTOMER 7"), asked);
    }

    private static final class Recording implements PartyProfileRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<PartyItemRow> items(PartyKind kind, int partyId, LocalDate from, LocalDate to) {
            calls.add("items " + from + ".." + to);
            return List.of();
        }

        @Override
        public List<PartyProfileDay> days(PartyKind kind, int partyId, LocalDate from, LocalDate to) {
            calls.add("days " + from + ".." + to);
            return List.of();
        }

        @Override
        public Optional<LocalDate> lastDocument(PartyKind kind, int partyId) {
            calls.add("last");
            return Optional.empty();
        }
    }
}
