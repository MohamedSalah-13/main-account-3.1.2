package com.hamza.account.features.party.payment;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertThrows(BusinessRuleException.class,
                () -> new PartyPaymentsService(repository).payments(PartyKind.CUSTOMER, FROM, TO));
        assertTrue(repository.calls.isEmpty());
    }

    /** The supplier report is the supplier button's key, not the customer's. */
    @Test
    void eachSideAsksTheKeyItsButtonNames() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_SALES);
        Recording repository = new Recording();

        new PartyPaymentsService(repository).payments(PartyKind.CUSTOMER, FROM, TO);
        assertThrows(BusinessRuleException.class,
                () -> new PartyPaymentsService(repository).payments(PartyKind.SUPPLIER, FROM, TO));
        assertEquals(List.of("CUSTOMER 2026-03-01..2026-03-31"), repository.calls);
    }

    @Test
    void aReversedPeriodIsRefusedInWordsNotRead() {
        signIn(AppPermissions.REPORTS_SHOW_PURCHASE);
        Recording repository = new Recording();

        assertThrows(UserValidationException.class,
                () -> new PartyPaymentsService(repository).payments(PartyKind.SUPPLIER, TO, FROM));
        assertThrows(UserValidationException.class,
                () -> new PartyPaymentsService(repository).payments(PartyKind.SUPPLIER, null, TO));
        assertTrue(repository.calls.isEmpty());
    }

    @Test
    void theSummaryIsAddedUpFromTheRowsShown() {
        List<PartyPaymentRow> rows = List.of(
                row(1, "500.00"), row(2, "250.50"), row(3, "-100.00"));

        PartyPaymentsSummary summary = PartyPaymentsSummary.of(rows);

        assertEquals(3, summary.movements());
        assertEquals(new BigDecimal("650.50"), summary.total());
        assertEquals(new PartyPaymentsSummary(0, BigDecimal.ZERO), PartyPaymentsSummary.of(List.of()));
    }

    private static PartyPaymentRow row(long id, String paid) {
        return new PartyPaymentRow(id, FROM, 7, "أحمد", new BigDecimal(paid), "الخزينة", 0, "");
    }

    private static final class Recording implements PartyPaymentsRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<PartyPaymentRow> between(PartyKind kind, LocalDate from, LocalDate to) {
            calls.add(kind + " " + from + ".." + to);
            return List.of();
        }
    }
}
