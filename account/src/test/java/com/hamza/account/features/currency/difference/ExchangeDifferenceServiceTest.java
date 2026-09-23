package com.hamza.account.features.currency.difference;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.RateInForce;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeDifferenceServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final Currency USD = new Currency(2, "USD", "دولار", "$", "", 2, false, true, 1);

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

    private static final class Recording implements ExchangeDifferenceRepository, ExchangeDifferenceService.Rates {
        final List<String> calls = new ArrayList<>();
        List<ExchangeAccount> accounts = List.of();

        @Override
        public List<ExchangeAccount> accounts() {
            calls.add("accounts");
            return accounts;
        }

        @Override
        public List<ExchangeMovement> movements(LocalDate through) {
            calls.add("movements " + through);
            return List.of(new ExchangeMovement(ExchangeAccountKind.TREASURY, 1, FROM, 10, 1,
                    new BigDecimal("100"), new BigDecimal("4800")));
        }

        @Override
        public List<Currency> currencies() {
            calls.add("currencies");
            return List.of(USD);
        }

        @Override
        public Map<Integer, RateInForce> inForce(LocalDate day) {
            calls.add("rates " + day);
            return Map.of(2, new RateInForce(2, day, new BigDecimal("50"), null));
        }
    }

    @Test
    @DisplayName("the profit key is asked before anything is read - it is a line of the profit and loss explained")
    void thePermissionComesFirst() {
        signIn(AppPermissions.CURRENCY_SHOW);
        Recording recording = new Recording();
        ExchangeDifferenceService service = new ExchangeDifferenceService(recording, recording);

        assertThrows(BusinessRuleException.class, () -> service.report(FROM, TO));
        assertThrows(BusinessRuleException.class, () -> service.figures(FROM, TO));
        assertTrue(recording.calls.isEmpty());
    }

    @Test
    @DisplayName("a shop with no foreign account reads the accounts and nothing else, and has no line to draw")
    void noForeignAccount() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording recording = new Recording();
        ExchangeDifferenceService service = new ExchangeDifferenceService(recording, recording);

        assertFalse(service.figures(FROM, TO).applies());
        assertEquals(List.of("accounts"), recording.calls);
    }

    @Test
    @DisplayName("the movements are read to the last day, and the rates on the day before the first and on the last")
    void readsTheTwoDays() throws Exception {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording recording = new Recording();
        recording.accounts = List.of(new ExchangeAccount(ExchangeAccountKind.TREASURY, 1, "Dollars", 2));
        ExchangeDifferenceService service = new ExchangeDifferenceService(recording, recording);

        ExchangeDifferenceReport report = service.report(FROM, TO);

        assertEquals(List.of("accounts", "currencies", "movements 2026-09-30", "rates 2026-08-31", "rates 2026-09-30"),
                recording.calls);
        assertEquals(0, new BigDecimal("200").compareTo(report.summary().result()));
    }

    @Test
    @DisplayName("a period that ends before it starts is refused")
    void periodOrder() {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording recording = new Recording();
        ExchangeDifferenceService service = new ExchangeDifferenceService(recording, recording);

        assertThrows(UserValidationException.class, () -> service.report(TO, FROM));
        assertThrows(UserValidationException.class, () -> service.report(null, TO));
    }
}
