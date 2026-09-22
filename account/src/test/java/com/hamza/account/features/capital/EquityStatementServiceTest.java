package com.hamza.account.features.capital;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquityStatementServiceTest {

    private static final CapitalFilter MARCH = new CapitalFilter(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

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
    void nothingIsReadWithoutTheCapitalPermission() {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording repository = new Recording();
        List<String> profitAsked = new ArrayList<>();

        EquityStatementService service = new EquityStatementService(repository, (from, to) -> {
            profitAsked.add(from + ".." + to);
            return List.of();
        });
        assertThrows(BusinessRuleException.class, () -> service.movements(MARCH));
        assertThrows(BusinessRuleException.class, () -> service.statement(MARCH));
        assertTrue(repository.calls.isEmpty());
        assertTrue(profitAsked.isEmpty());
    }

    /** The movements are the capital permission's alone; the profit is asked of its own service. */
    @Test
    void theMovementsNeedTheCapitalPermissionAlone() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL);
        Recording repository = new Recording();

        new EquityStatementService(repository, (from, to) -> List.of()).movements(MARCH);

        assertEquals(List.of("days 2026-03-01..2026-03-31"), repository.calls);
    }

    @Test
    void theStatementAsksTheProfitForThePeriodAndForEverythingBefore() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL);
        Recording repository = new Recording();
        List<String> profitAsked = new ArrayList<>();

        EquityStatement statement = new EquityStatementService(repository, (from, to) -> {
            profitAsked.add(from + ".." + to);
            return List.of();
        }).statement(MARCH);

        assertEquals(List.of("2026-03-01..2026-03-31", "null..2026-02-28"), profitAsked);
        assertEquals(List.of("forward", "before 2026-03-01", "days 2026-03-01..2026-03-31"), repository.calls);
        assertEquals(0, statement.opening().signum());
    }

    @Test
    void theReconciliationNeedsTheCapitalPermission() {
        signIn(AppPermissions.REPORTS_SHOW_PROFIT);
        Recording repository = new Recording();

        assertThrows(BusinessRuleException.class, () -> new EquityStatementService(repository, (from, to) -> List.of())
                .reconciliation(LocalDate.of(2026, 3, 31)));
        assertTrue(repository.calls.isEmpty());
    }

    /** The equity side is the statement's close on the same day, read through the statement itself. */
    @Test
    void theReconciliationSetsTheFiguresAgainstTheStatementsClose() throws Exception {
        signIn(AppPermissions.TREASURY_CAPITAL);
        Recording repository = new Recording();
        List<String> profitAsked = new ArrayList<>();

        EquityReconciliation reconciliation = new EquityStatementService(repository, (from, to) -> {
            profitAsked.add(from + ".." + to);
            return List.of();
        }).reconciliation(LocalDate.of(2026, 3, 31));

        assertEquals(List.of("2026-03-31..2026-03-31", "null..2026-03-30"), profitAsked);
        assertEquals(List.of("forward", "before 2026-03-31", "days 2026-03-31..2026-03-31", "reconciliation"),
                repository.calls);
        assertEquals(0, reconciliation.equity().signum());
        assertEquals(0, new BigDecimal("1500").compareTo(reconciliation.netAssets()), "1000 + 300 + 400 - 200");
        assertEquals(0, new BigDecimal("1500").compareTo(reconciliation.unexplained()));
    }

    private static final class Recording implements CapitalRepository {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<CapitalDay> days(LocalDate from, LocalDate to) {
            calls.add("days " + from + ".." + to);
            return List.of();
        }

        @Override
        public CapitalBefore before(LocalDate day) {
            calls.add("before " + day);
            return new CapitalBefore(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        @Override
        public BroughtForward broughtForward() {
            calls.add("forward");
            return BroughtForward.NONE;
        }

        @Override
        public ReconciliationFigures reconciliation() {
            calls.add("reconciliation");
            return new ReconciliationFigures(new BigDecimal("1000"), new BigDecimal("300"), BigDecimal.ZERO,
                    new BigDecimal("200"), BigDecimal.ZERO, new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
    }
}
