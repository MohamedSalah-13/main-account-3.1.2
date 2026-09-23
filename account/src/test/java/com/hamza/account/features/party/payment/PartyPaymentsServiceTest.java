package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyPaymentsServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

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

    @Test
    void thePermissionIsAskedBeforeAnythingIsRead() {
        signIn();
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class, () -> new PartyPaymentsService(repository)
                .payments(PartyPaymentsFilter.of(PartyKind.CUSTOMER, FROM, TO)));
        assertThrows(BusinessRuleException.class,
                () -> new PartyPaymentsService(repository).treasuries(PartyKind.CUSTOMER));
        assertTrue(repository.calls.isEmpty());
    }

    /** The supplier report is the suppliers' key, not the customers'. */
    @Test
    void eachSideAsksItsOwnKey() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();

        new PartyPaymentsService(repository).payments(PartyPaymentsFilter.of(PartyKind.CUSTOMER, FROM, TO));
        assertThrows(BusinessRuleException.class, () -> new PartyPaymentsService(repository)
                .payments(PartyPaymentsFilter.of(PartyKind.SUPPLIER, FROM, TO)));
        assertEquals(List.of("CUSTOMER 2026-03-01..2026-03-31"), repository.calls);
    }

    @Test
    void aReversedPeriodIsRefusedInWordsNotRead() {
        signIn(AppPermissions.REPORTS_SHOW_PURCHASE);
        Recording repository = new Recording();

        assertThrows(UserValidationException.class, () -> new PartyPaymentsService(repository)
                .payments(PartyPaymentsFilter.of(PartyKind.SUPPLIER, TO, FROM)));
        assertThrows(UserValidationException.class, () -> new PartyPaymentsService(repository)
                .payments(PartyPaymentsFilter.of(PartyKind.SUPPLIER, null, TO)));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    @DisplayName("the screen offers the sides this edition carries and this reader may read, customers first")
    void theSidesOffered() {
        Set<Object> everything = Set.of(AppPermissions.REPORTS_SHOW_SALES, AppPermissions.REPORTS_SHOW_PURCHASE,
                ProductFeatures.REPORT_CUSTOMER_PAYMENTS, ProductFeatures.REPORT_SUPPLIER_PAYMENTS);
        assertEquals(List.of(PartyKind.CUSTOMER, PartyKind.SUPPLIER),
                PartyPaymentsService.offeredSides(everything::contains, everything::contains));
        assertEquals(List.of(PartyKind.SUPPLIER), PartyPaymentsService.offeredSides(
                key -> key.equals(AppPermissions.REPORTS_SHOW_PURCHASE), everything::contains),
                "a purchasing clerk sees the suppliers alone");
        assertEquals(List.of(PartyKind.CUSTOMER), PartyPaymentsService.offeredSides(everything::contains,
                feature -> feature.equals(ProductFeatures.REPORT_CUSTOMER_PAYMENTS)),
                "an edition without the suppliers' report offers none of it");
        assertTrue(PartyPaymentsService.offeredSides(key -> false, everything::contains).isEmpty());
    }

    @Test
    @DisplayName("the screen opens on the side asked for when offered, else the first - and the sidebar asks for none")
    void theSideTheScreenOpensOn() {
        // List.copyOf is what the screen holds, and it is the list that throws on contains(null).
        List<PartyKind> both = List.copyOf(List.of(PartyKind.CUSTOMER, PartyKind.SUPPLIER));
        List<PartyKind> suppliers = List.copyOf(List.of(PartyKind.SUPPLIER));

        assertEquals(PartyKind.CUSTOMER, PartyPaymentsService.openingSide(both, null),
                "the sidebar's button prefers no side");
        assertEquals(PartyKind.SUPPLIER, PartyPaymentsService.openingSide(both, PartyKind.SUPPLIER));
        assertEquals(PartyKind.SUPPLIER, PartyPaymentsService.openingSide(suppliers, null));
        assertEquals(PartyKind.SUPPLIER, PartyPaymentsService.openingSide(suppliers, PartyKind.CUSTOMER),
                "a side not offered is not opened on");
        assertNull(PartyPaymentsService.openingSide(List.of(), null));
    }

    @Test
    @DisplayName("the summary splits what came in from what went back, from the very rows shown")
    void theSummaryIsAddedUpFromTheRowsShown() {
        List<PartyPaymentRow> rows = List.of(row(1, "500.00"), row(2, "250.50"), row(3, "-100.00"));

        PartyPaymentsSummary summary = PartyPaymentsSummary.of(rows);

        assertEquals(3, summary.movements());
        assertEquals(new BigDecimal("750.50"), summary.received());
        assertEquals(new BigDecimal("100.00"), summary.returned());
        assertEquals(new BigDecimal("650.50"), summary.total());
        assertEquals(new PartyPaymentsSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                PartyPaymentsSummary.of(List.of()));
    }

    private static PartyPaymentRow row(long id, String paid) {
        return new PartyPaymentRow(id, FROM, 7, "أحمد", new BigDecimal(paid), "الخزينة", 0, "", "admin");
    }

    private static final class Recording implements PartyPaymentsRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<PartyPaymentRow> page(PartyPaymentsFilter filter) {
            calls.add(filter.kind() + " " + filter.from() + ".." + filter.to());
            return List.of();
        }

        @Override
        public List<TreasuryOption> treasuries() {
            calls.add("treasuries");
            return List.of();
        }
    }
}
