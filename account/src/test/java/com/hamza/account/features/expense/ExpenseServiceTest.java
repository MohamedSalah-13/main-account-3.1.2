package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ExpensesChanged;
import com.hamza.account.features.events.TreasuryBalancesChanged;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.shift.ShiftCashLedger;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.shift.ShiftGate;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hamza.account.features.expense.ExpenseFixtures.DAY;
import static com.hamza.account.features.expense.ExpenseFixtures.ELECTRICITY;
import static com.hamza.account.features.expense.ExpenseFixtures.OLD_RENT;
import static com.hamza.account.features.expense.ExpenseFixtures.OPERATOR;
import static com.hamza.account.features.expense.ExpenseFixtures.SALARIES;
import static com.hamza.account.features.expense.ExpenseFixtures.entry;
import static com.hamza.account.features.expense.ExpenseFixtures.row;
import static com.hamza.account.features.expense.ExpenseFixtures.signInWith;
import static com.hamza.account.features.expense.ExpenseFixtures.till;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link ExpenseService} decides before and around a write, with the transaction run directly.
 * <p>
 * What is <b>not</b> covered here and is written down rather than implied: that a refused line of a
 * batch takes back the lines already written. That needs a real transaction, so the only honest proof
 * is a run against MySQL. Nor the shift gate's own refusals - the gate here is {@code ShiftGate.disabled()},
 * and {@code ShiftGateTest} is where the gate is tested.
 */
class ExpenseServiceTest {

    private final ExpenseFixtures.Repository repository = new ExpenseFixtures.Repository();
    private final ExpenseFixtures.Headings headings =
            new ExpenseFixtures.Headings(ELECTRICITY, SALARIES, OLD_RENT);
    private final List<AppEvent> announced = new ArrayList<>();
    private final Map<Integer, TreasuryBalanceSummary> tills = new java.util.HashMap<>(Map.of(
            1, till(1, "100.00"), 2, till(2, "40.00")));

    private final ExpenseService service = new ExpenseService(repository, headings, ShiftGate.disabled(),
            ShiftCashLedger.disabled(), this::storedEffect, new ExpenseService.TreasuryBalances() {
        @Override
        public List<TreasuryBalanceSummary> active() {
            return List.copyOf(tills.values());
        }

        @Override
        public TreasuryBalanceSummary find(int treasuryId) {
            return tills.get(treasuryId);
        }
    }, recording(), ExpenseTransactions.direct());

    private ShiftCashEffect storedEffect(int expenseId) {
        ExpenseRow stored = repository.rows.get(expenseId);
        return stored == null ? null : ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, expenseId,
                stored.treasuryId(), null, stored.amount());
    }

    private ChangeAnnouncer recording() {
        return announced::add;
    }

    // ---- reading ------------------------------------------------------------------------

    @Test
    @DisplayName("the list asks expenses.show, which nothing read before")
    void readingIsGuarded() {
        signInWith(AppPermissions.TREASURY_SHOW, AppPermissions.EXPENSES_CREATE);
        assertThrows(BusinessRuleException.class, () -> service.search(ExpenseFilter.between(DAY, DAY)));
        assertThrows(BusinessRuleException.class, () -> service.find(1));
        assertThrows(BusinessRuleException.class, () -> service.users());
    }

    @Test
    @DisplayName("printing and exporting ask expenses.export on top of viewing")
    void exportIsItsOwnPermission() throws Exception {
        signInWith(AppPermissions.EXPENSES_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.forPrint(ExpenseFilter.between(DAY, DAY)));

        signInWith(AppPermissions.EXPENSES_SHOW, AppPermissions.EXPENSES_EXPORT);
        assertEquals(0, service.forPrint(ExpenseFilter.between(DAY, DAY)).rows().size());
    }

    @Test
    @DisplayName("the summary carries the period of equal length before, and nothing without a whole period")
    void previousPeriodIsMeasured() throws Exception {
        signInWith(AppPermissions.EXPENSES_SHOW);
        repository.totalsByFrom.put(DAY.withDayOfMonth(1), new BigDecimal("300.00"));
        repository.totalsByFrom.put(DAY.withDayOfMonth(1).minusDays(10), new BigDecimal("200.00"));

        ExpenseSummary summary = service.search(ExpenseFilter.between(DAY.withDayOfMonth(1), DAY)).summary();

        assertEquals(2, repository.summarized.size(), "the period and the one before it");
        assertEquals(DAY.withDayOfMonth(1).minusDays(10), repository.summarized.get(1).from());
        assertEquals(new BigDecimal("200.00"), summary.previousTotal());
        assertEquals(new BigDecimal("50.0"), summary.changePercent());

        repository.summarized.clear();
        assertNull(service.search(ExpenseFilter.between(null, DAY)).summary().previousTotal());
        assertEquals(1, repository.summarized.size(), "no previous period to ask about");
    }

    @Test
    @DisplayName("a till's balance goes only to a reader who may see treasuries")
    void balancesAreWithheld() throws Exception {
        signInWith(AppPermissions.EXPENSES_CREATE);
        assertTrue(service.treasuries().stream().allMatch(treasury -> treasury.balance() == null));

        signInWith(AppPermissions.EXPENSES_CREATE, AppPermissions.TREASURY_SHOW);
        assertTrue(service.treasuries().stream().allMatch(treasury -> treasury.balance() != null));
    }

    @Test
    @DisplayName("a till in a foreign currency is not offered for an expense, and is refused if named (V81)")
    void aForeignTillIsNotAnExpenseTill() throws Exception {
        tills.put(9, new TreasuryBalanceSummary(9, "درج الدولار", null, true, 0, null, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4800"), 3, BigDecimal.ZERO, new BigDecimal("100")));
        signInWith(AppPermissions.EXPENSES_CREATE, AppPermissions.TREASURY_SHOW);
        assertTrue(service.treasuries().stream().noneMatch(TreasuryBalanceSummary::isForeign));
        assertThrows(BusinessRuleException.class,
                () -> service.create(ExpenseFixtures.entry(ELECTRICITY.id(), 9, "10.00")));
        assertTrue(repository.inserts.isEmpty(), "nothing was written");
    }

    // ---- the balance warning (م-١) -------------------------------------------------------

    @Test
    @DisplayName("a new expense is short by what the till does not hold")
    void shortfallOfANewExpense() throws Exception {
        signInWith(AppPermissions.EXPENSES_CREATE);
        assertEquals(new BigDecimal("50.00"), service.shortfall(1, new BigDecimal("150.00"), 0));
        assertEquals(0, service.shortfall(1, new BigDecimal("100.00"), 0).signum());
    }

    @Test
    @DisplayName("a correction on the same till gets its own stored amount back first; on another till it does not")
    void shortfallOfACorrection() throws Exception {
        signInWith(AppPermissions.EXPENSES_UPDATE);
        repository.rows.put(3, row(3, ELECTRICITY, 1, "80.00", null));

        assertEquals(0, service.shortfall(1, new BigDecimal("150.00"), 3).signum(),
                "100 in the till and 80 of it already this expense's: 150 fits");
        assertEquals(new BigDecimal("110.00"), service.shortfall(2, new BigDecimal("150.00"), 3),
                "moved to a till holding 40, nothing comes back to it");
    }

    // ---- writing ------------------------------------------------------------------------

    @Test
    @DisplayName("an expense is written as the signed-in user, with no employee, and announced twice over")
    void createWrites() throws Exception {
        signInWith(AppPermissions.EXPENSES_CREATE);
        int id = service.create(entry(ELECTRICITY.id(), 1, "25.00"));

        assertEquals(101, id);
        ExpenseFixtures.Repository.Insert written = repository.inserts.get(0);
        assertNull(written.employeeId());
        assertNull(written.shiftId(), "shifts are off: the gate answers with no shift");
        assertEquals(OPERATOR, written.userId());
        assertEquals(List.of(new ExpensesChanged(), new TreasuryBalancesChanged()), announced);
    }

    @Test
    @DisplayName("creating asks expenses.create, and a refusal writes and announces nothing")
    void createIsGuarded() {
        signInWith(AppPermissions.EXPENSES_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.create(entry(ELECTRICITY.id(), 1, "25.00")));
        assertTrue(repository.inserts.isEmpty());
        assertTrue(announced.isEmpty());
    }

    @Test
    @DisplayName("the expenses screen cannot file under a heading employees are paid under, or a stopped one")
    void headingsTheExpensesScreenMayNotUse() {
        signInWith(AppPermissions.EXPENSES_CREATE);
        UserValidationException employee = assertThrows(UserValidationException.class,
                () -> service.create(entry(SALARIES.id(), 1, "25.00")));
        assertEquals("expense.error.heading.employee", employee.getMessage());

        UserValidationException stopped = assertThrows(UserValidationException.class,
                () -> service.create(entry(OLD_RENT.id(), 1, "25.00")));
        assertEquals("expense.error.heading.stopped", stopped.getMessage());
        assertTrue(repository.inserts.isEmpty());
    }

    @Test
    @DisplayName("an employee is paid only under a heading marked for employees, and the employee is written")
    void employeePayments() throws Exception {
        signInWith(AppPermissions.EXPENSES_CREATE);
        UserValidationException wrong = assertThrows(UserValidationException.class,
                () -> service.recordForEmployee(entry(ELECTRICITY.id(), 1, "500.00"), 4));
        assertEquals("expense.error.heading.not.employee", wrong.getMessage());

        service.recordForEmployee(entry(SALARIES.id(), 1, "500.00"), 4);
        assertEquals(4, repository.inserts.get(0).employeeId());
    }

    @Test
    @DisplayName("a refused line refuses the batch and names the line, one-based")
    void batchNamesTheRefusedLine() {
        signInWith(AppPermissions.EXPENSES_CREATE);
        ExpenseBatchLineRefused refused = assertThrows(ExpenseBatchLineRefused.class,
                () -> service.createBatch(List.of(entry(ELECTRICITY.id(), 1, "10.00"),
                        entry(SALARIES.id(), 1, "20.00"), entry(ELECTRICITY.id(), 1, "30.00"))));

        assertEquals(2, refused.line());
        assertEquals("expense.error.heading.employee", refused.userMessage());
        assertEquals(1, repository.inserts.size(),
                "the first line was written before the second refused - the real transaction takes it back, "
                        + "which is the acceptance test's to prove");
    }

    @Test
    @DisplayName("an empty batch is refused")
    void emptyBatch() {
        signInWith(AppPermissions.EXPENSES_CREATE);
        UserValidationException empty = assertThrows(UserValidationException.class,
                () -> service.createBatch(List.of()));
        assertEquals("expense.batch.error.empty", empty.getMessage());
    }

    @Test
    @DisplayName("a correction keeps the kind of heading the row was paid under")
    void correctionKeepsTheHeadingKind() throws Exception {
        signInWith(AppPermissions.EXPENSES_UPDATE);
        repository.rows.put(3, row(3, SALARIES, 1, "500.00", 4));

        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> service.update(entry(ELECTRICITY.id(), 1, "450.00").withId(3), "typo"));
        assertEquals("expense.error.heading.not.employee", refused.getMessage());

        assertEquals(1, service.update(entry(SALARIES.id(), 1, "450.00").withId(3), "typo"));
        assertEquals(new BigDecimal("450.00"), repository.updates.get(0).amount());
    }

    @Test
    @DisplayName("a correction may stay on a heading stopped since, and may not move onto one")
    void correctionAndStoppedHeadings() throws Exception {
        signInWith(AppPermissions.EXPENSES_UPDATE);
        repository.rows.put(3, row(3, OLD_RENT, 1, "300.00", null));
        assertEquals(1, service.update(entry(OLD_RENT.id(), 1, "310.00").withId(3), "typo"));

        repository.rows.put(4, row(4, ELECTRICITY, 1, "30.00", null));
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> service.update(entry(OLD_RENT.id(), 1, "30.00").withId(4), "typo"));
        assertEquals("expense.error.heading.stopped", refused.getMessage());
    }

    @Test
    @DisplayName("correcting an expense that is gone says so")
    void correctionOfAMissingExpense() {
        signInWith(AppPermissions.EXPENSES_UPDATE);
        UserValidationException missing = assertThrows(UserValidationException.class,
                () -> service.update(entry(ELECTRICITY.id(), 1, "10.00").withId(99), "typo"));
        assertEquals("expense.error.not.found", missing.getMessage());
        assertNotNull(missing);
    }

    @Test
    @DisplayName("payee suggestions need something typed, and nothing else")
    void payeeSuggestions() throws Exception {
        assertTrue(service.payeeSuggestions(" ").isEmpty());
        assertEquals(List.of("شركة-payee"), service.payeeSuggestions("شركة"));
    }
}
