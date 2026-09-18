package com.hamza.account.features.expense.budget;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.ExpenseHeadingUsage;
import com.hamza.account.features.expense.ExpenseHeadingDraft;
import com.hamza.account.features.expense.report.ExpenseReportRows;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may read a budget and who may set one - two different permissions, on purpose.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} is {@code currentUserId() == 1} and
 * bypasses every permission, so a test signed in as user 1 proves nothing about authorization.
 */
class ExpenseBudgetServiceTest {

    private static final int OPERATOR = 9;
    private static final ExpenseHeading POWER = new ExpenseHeading(11, "كهرباء", 10, "إدارية", true, null, false);
    private static final ExpenseHeading OLD = new ExpenseHeading(12, "قديم", null, null, false, null, false);

    private final Budgets budgets = new Budgets();
    private final Headings headings = new Headings(POWER, OLD);
    private final List<ExpenseFilter> actualsAsked = new ArrayList<>();
    private final ExpenseBudgetService service = new ExpenseBudgetService(budgets, headings, filter -> {
        actualsAsked.add(filter);
        return List.of(new ExpenseReportRows.HeadingTotal(POWER.id(), 2, new BigDecimal("800")));
    });

    private static void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static ExpenseBudgetDraft draft(int id, int headingId, String amount) {
        return new ExpenseBudgetDraft(id, headingId, 2026, 9, new BigDecimal(amount), "");
    }

    @Test
    @DisplayName("reading a budget asks the reports permission, not the one that sets it")
    void readingAsksForReports() {
        signInWith(AppPermissions.EXPENSES_BUDGET_MANAGE);
        assertThrows(BusinessRuleException.class, () -> service.byYear(2026));
        assertThrows(BusinessRuleException.class, () -> service.years());
        assertThrows(BusinessRuleException.class,
                () -> service.report(ExpenseFilter.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))));
        assertTrue(actualsAsked.isEmpty(), "nothing is read at all when the reader may not read it");
    }

    @Test
    @DisplayName("setting one asks expenses.budget.manage, and a refusal writes nothing")
    void writingIsGuarded() {
        signInWith(AppPermissions.EXPENSES_REPORTS, AppPermissions.EXPENSES_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.save(draft(0, POWER.id(), "1000")));
        assertThrows(BusinessRuleException.class, () -> service.delete(4));
        assertTrue(budgets.inserted.isEmpty());
        assertTrue(budgets.deleted.isEmpty());
    }

    @Test
    @DisplayName("a new budget answers its generated code; a correction answers its own")
    void saveAnswersTheCode() throws Exception {
        signInWith(AppPermissions.EXPENSES_BUDGET_MANAGE);
        assertEquals(70, service.save(draft(0, POWER.id(), "1000")));
        assertEquals(5, service.save(draft(5, POWER.id(), "1200")));
        assertEquals(1, budgets.inserted.size());
        assertEquals(1, budgets.updated.size());
    }

    @Test
    @DisplayName("the rules are asked before anything is written - a stopped heading, and a duplicate")
    void rulesRefuseBeforeTheWrite() {
        signInWith(AppPermissions.EXPENSES_BUDGET_MANAGE);
        assertEquals("expense.budget.error.heading.stopped",
                assertThrows(UserValidationException.class,
                        () -> service.save(draft(0, OLD.id(), "1000"))).getMessage());

        budgets.taken = true;
        assertEquals("expense.budget.error.duplicate.month",
                assertThrows(UserValidationException.class,
                        () -> service.save(draft(0, POWER.id(), "1000"))).getMessage());
        assertTrue(budgets.inserted.isEmpty());
    }

    @Test
    @DisplayName("a scope with no whole period is refused in words - a budget is for a period")
    void openPeriodIsRefused() {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        assertEquals("expense.report.error.period",
                assertThrows(UserValidationException.class,
                        () -> service.report(ExpenseFilter.between(LocalDate.of(2026, 9, 1), null))).getMessage());
        assertTrue(actualsAsked.isEmpty());
    }

    @Test
    @DisplayName("the spend comes from the report by heading's own rows, over the report's own scope")
    void actualsAreTheReportsOwn() throws Exception {
        signInWith(AppPermissions.EXPENSES_REPORTS);
        ExpenseFilter scope = ExpenseFilter.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        budgets.forPeriod.add(new ExpenseBudget(1, POWER.id(), POWER.name(), "إدارية", 2026, 9,
                new BigDecimal("1000"), ""));

        ExpenseBudgetReport report = service.report(scope);
        assertEquals(List.of(scope), actualsAsked, "the same filter, so the two sides describe one set");
        assertSame(scope, report.scope());
        assertEquals(0, new BigDecimal("800").compareTo(report.actual()));
        assertEquals(0, new BigDecimal("1000").compareTo(report.budget()));
    }

    // ---- stand-ins ----------------------------------------------------------------------

    private static final class Budgets implements ExpenseBudgetRepository {

        final List<ExpenseBudgetDraft> inserted = new ArrayList<>();
        final List<ExpenseBudgetDraft> updated = new ArrayList<>();
        final List<Integer> deleted = new ArrayList<>();
        final List<ExpenseBudget> forPeriod = new ArrayList<>();
        boolean taken;

        @Override
        public List<ExpenseBudget> byYear(int year) {
            return List.of();
        }

        @Override
        public List<ExpenseBudget> forPeriod(LocalDate from, LocalDate to) {
            return List.copyOf(forPeriod);
        }

        @Override
        public ExpenseBudget find(int id) {
            return null;
        }

        @Override
        public boolean taken(int headingId, int year, Integer month, int exceptId) {
            return taken;
        }

        @Override
        public List<Integer> years() {
            return List.of(2026);
        }

        @Override
        public int insert(ExpenseBudgetDraft draft, int userId) {
            inserted.add(draft);
            return 70;
        }

        @Override
        public int update(ExpenseBudgetDraft draft) {
            updated.add(draft);
            return 1;
        }

        @Override
        public int delete(int id) {
            deleted.add(id);
            return 1;
        }
    }

    private record Headings(Map<Integer, ExpenseHeading> byId) implements ExpenseHeadingRepository {

        Headings(ExpenseHeading... headings) {
            this(new LinkedHashMap<>());
            for (ExpenseHeading heading : headings) {
                byId.put(heading.id(), heading);
            }
        }

        @Override
        public List<ExpenseHeading> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public ExpenseHeading find(int id) {
            return byId.get(id);
        }

        @Override
        public ExpenseHeading bySystemKey(String systemKey) {
            return null;
        }

        @Override
        public Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) {
            return Map.of();
        }

        @Override
        public boolean nameTaken(String name, int exceptId) {
            return false;
        }

        @Override
        public int insert(ExpenseHeadingDraft draft, int userId) {
            return 0;
        }

        @Override
        public int update(ExpenseHeadingDraft draft) {
            return 0;
        }

        @Override
        public int delete(int id) {
            return 0;
        }
    }
}
