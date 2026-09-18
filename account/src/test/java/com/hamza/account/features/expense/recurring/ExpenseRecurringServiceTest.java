package com.hamza.account.features.expense.recurring;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingDraft;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.ExpenseHeadingUsage;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may be reminded, who may manage the templates, and what a template's history protects.
 * <p>
 * <b>The session is never user 1</b>, which bypasses every permission.
 */
class ExpenseRecurringServiceTest {

    private static final int OPERATOR = 9;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    private static final ExpenseHeading RENT = new ExpenseHeading(11, "إيجار", 10, "إدارية", true, null, false);
    private static final ExpenseHeading SALARIES = new ExpenseHeading(1, "مرتبات", null, null, true, null, true);

    private final Templates templates = new Templates();
    private final ExpenseRecurringService service =
            new ExpenseRecurringService(templates, new Headings(RENT, SALARIES));

    private static void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static ExpenseRecurring template(int id, boolean active) {
        return new ExpenseRecurring(id, RENT.id(), RENT.name(), "إدارية", 1, "الخزينة",
                new BigDecimal("5000"), "المالك", "", ExpenseFrequency.MONTHLY, 1,
                LocalDate.of(2026, 1, 1), null, active);
    }

    private static ExpenseRecurringDraft draft(int id) {
        return new ExpenseRecurringDraft(id, RENT.id(), 1, new BigDecimal("5000"), "المالك", "",
                ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true);
    }

    @Test
    @DisplayName("being reminded asks expenses.show - the person who records it does not own the template")
    void beingRemindedIsTheListPermission() throws Exception {
        templates.rows.add(template(1, true));
        signInWith(AppPermissions.EXPENSES_SHOW);

        List<ExpenseRecurringDue> due = service.due(TODAY);
        assertEquals(List.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
                due.stream().map(ExpenseRecurringDue::periodStart).toList());
        // But managing them is a different act, and the reader may not.
        assertThrows(BusinessRuleException.class, service::all);
        assertThrows(BusinessRuleException.class, () -> service.save(draft(0)));
    }

    @Test
    @DisplayName("managing asks its own permission, and is not enough to be reminded")
    void managingIsItsOwnPermission() throws Exception {
        templates.rows.add(template(1, true));
        signInWith(AppPermissions.EXPENSES_RECURRING_MANAGE);
        assertEquals(1, service.all().size());
        assertThrows(BusinessRuleException.class, () -> service.due(TODAY));
    }

    @Test
    @DisplayName("nothing active means nothing is read at all")
    void noActiveTemplates() throws Exception {
        signInWith(AppPermissions.EXPENSES_SHOW);
        assertTrue(service.due(TODAY).isEmpty());
        assertTrue(templates.lookedBackTo.isEmpty(), "the recorded periods are not read for an empty list");
    }

    @Test
    @DisplayName("the reminder looks back two years, not over the whole history")
    void lookback() throws Exception {
        templates.rows.add(template(1, true));
        signInWith(AppPermissions.EXPENSES_SHOW);
        service.due(TODAY);
        assertEquals(List.of(TODAY.minusYears(2)), templates.lookedBackTo);
    }

    @Test
    @DisplayName("a period already recorded is quiet - the link is what says so")
    void recordedIsQuiet() throws Exception {
        templates.rows.add(template(1, true));
        templates.recorded.put(1, Set.of(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)));
        signInWith(AppPermissions.EXPENSES_SHOW);
        assertTrue(service.due(TODAY).isEmpty());
    }

    @Test
    @DisplayName("a salary heading is refused before anything is written")
    void employeeHeadingRefused() {
        signInWith(AppPermissions.EXPENSES_RECURRING_MANAGE);
        assertEquals("expense.recurring.error.heading.employee",
                assertThrows(UserValidationException.class, () -> service.save(
                        new ExpenseRecurringDraft(0, SALARIES.id(), 1, new BigDecimal("5000"), null, null,
                                ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, true))).getMessage());
        assertTrue(templates.inserted.isEmpty());
    }

    @Test
    @DisplayName("a template that has recorded expenses is refused a delete, and is stopped instead")
    void deleteRefusedOnceItHasRecorded() throws Exception {
        signInWith(AppPermissions.EXPENSES_RECURRING_MANAGE);
        templates.recordedCount = 3;
        assertEquals("expense.recurring.error.recorded",
                assertThrows(UserValidationException.class, () -> service.delete(1)).getMessage());
        assertTrue(templates.deleted.isEmpty(), "the rows carry its id and the key sets them to NULL");

        // Stopping it is the way out, and it goes through the ordinary save.
        assertEquals(1, service.save(new ExpenseRecurringDraft(1, RENT.id(), 1, new BigDecimal("5000"), null,
                null, ExpenseFrequency.MONTHLY, 1, LocalDate.of(2026, 1, 1), null, false)));
        assertEquals(1, templates.updated.size());

        templates.recordedCount = 0;
        assertEquals(1, service.delete(1));
        assertEquals(List.of(1), templates.deleted);
    }

    @Test
    @DisplayName("a new template answers its generated code; a correction answers its own")
    void saveAnswersTheCode() throws Exception {
        signInWith(AppPermissions.EXPENSES_RECURRING_MANAGE);
        assertEquals(80, service.save(draft(0)));
        assertEquals(6, service.save(draft(6)));
    }

    // ---- stand-ins ----------------------------------------------------------------------

    private static final class Templates implements ExpenseRecurringRepository {

        final List<ExpenseRecurring> rows = new ArrayList<>();
        final Map<Integer, Set<LocalDate>> recorded = new HashMap<>();
        final List<ExpenseRecurringDraft> inserted = new ArrayList<>();
        final List<ExpenseRecurringDraft> updated = new ArrayList<>();
        final List<Integer> deleted = new ArrayList<>();
        final List<LocalDate> lookedBackTo = new ArrayList<>();
        int recordedCount;

        @Override
        public List<ExpenseRecurring> all() {
            return List.copyOf(rows);
        }

        @Override
        public List<ExpenseRecurring> active() {
            return rows.stream().filter(ExpenseRecurring::active).toList();
        }

        @Override
        public ExpenseRecurring find(int id) {
            return rows.stream().filter(row -> row.id() == id).findFirst().orElse(null);
        }

        @Override
        public Map<Integer, Set<LocalDate>> recordedPeriods(LocalDate since) {
            lookedBackTo.add(since);
            return Map.copyOf(recorded);
        }

        @Override
        public int recordedCount(int id) {
            return recordedCount;
        }

        @Override
        public int insert(ExpenseRecurringDraft draft, int userId) {
            inserted.add(draft);
            return 80;
        }

        @Override
        public int update(ExpenseRecurringDraft draft) {
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
