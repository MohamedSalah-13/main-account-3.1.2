package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.employee.statement.EmployeeStatementFilter;
import com.hamza.account.features.employee.statement.EmployeeStatementRepository;
import com.hamza.account.features.employee.statement.EmployeeStatementRow;
import com.hamza.account.features.employee.statement.EmployeeStatementService;
import com.hamza.account.features.employee.statement.EmployeeStatementSummary;
import com.hamza.account.features.employee.statement.EmployeeStatementUserOption;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the account services decide before they reach a database.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} is {@code currentUserId() == 1}
 * and bypasses every permission, so a test signed in as user 1 proves nothing about authorization.
 * <p>
 * What is <b>not</b> covered here and is written down rather than implied: everything inside
 * {@code TransactionTemplate} — a payment writing its expense row and its purpose row together, and
 * the rollback that must take both if either fails. That needs a real transaction, so the only
 * honest proof is a run against MySQL.
 */
class EmployeeAccountServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);

    private final FakeStatementRepository repository = new FakeStatementRepository();
    private final EmployeeStatementService statements = new EmployeeStatementService(repository);
    private final EmployeeLedgerService ledger = new EmployeeLedgerService(repository);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    private static EmployeeStatementFilter filter() {
        return EmployeeStatementFilter.all(7, FROM, TO);
    }

    @Test
    @DisplayName("reading an account needs its own permission, not the employees screen's")
    void readingIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);

        assertThrows(BusinessRuleException.class, () -> statements.search(filter()));
        assertThrows(BusinessRuleException.class, () -> statements.forPrint(filter()));
        assertThrows(BusinessRuleException.class, () -> statements.currentBalance(7));
        assertThrows(BusinessRuleException.class, () -> statements.earliestMovement(7));
        assertThrows(BusinessRuleException.class, () -> statements.usersWhoEntered(7));
    }

    @Test
    @DisplayName("and is allowed with it")
    void readingIsAllowed() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        assertEquals(0, statements.search(filter()).rows().size());
        assertEquals(new BigDecimal("350.00"), statements.currentBalance(7));
    }

    @Test
    @DisplayName("recording a movement needs the adjust permission, and paying does not grant it")
    void recordingIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_PAY, AppPermissions.EMPLOYEE_ACCOUNT_SHOW);

        assertThrows(BusinessRuleException.class, () -> ledger.record(deduction()),
                "the person who counts the drawer is not the person who decides an employee "
                        + "owes two hundred");
    }

    @Test
    @DisplayName("and writes the movement with the signed-in user on it when it is granted")
    void recordingIsAllowed() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);

        ledger.record(deduction());

        assertEquals("DEDUCTION", repository.lastKind);
        assertEquals(new BigDecimal("150.00"), repository.lastAmount);
        assertEquals(9, repository.lastUserId, "who entered it, not user 1");
    }

    @Test
    @DisplayName("a row a payroll run wrote is refused, with the key that says why")
    void aRunsRowIsNotRemovedByHand() {
        signInWith(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        repository.runOfEntry = 42;

        assertEquals("employee.error.account.payroll.row",
                assertThrows(UserValidationException.class, () -> ledger.remove(7, 3)).getMessage());
    }

    @Test
    @DisplayName("one a person entered is removed")
    void aHandEnteredRowIsRemoved() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_ACCOUNT_ADJUST);
        repository.runOfEntry = null;

        assertEquals(1, ledger.remove(7, 3));
        assertEquals(3, repository.deletedEntryId);
    }

    @Test
    @DisplayName("removing needs the permission too - a read cannot become a delete")
    void removingIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_ACCOUNT_SHOW);
        assertThrows(BusinessRuleException.class, () -> ledger.remove(7, 3));
    }

    private static EmployeeLedgerEntry deduction() throws UserValidationException {
        return EmployeeLedgerEntry.parse(7, LocalDate.of(2026, 2, 20),
                EmployeeEntryKind.DEDUCTION, new BigDecimal("150"), "غياب يوم");
    }

    /** Records what it was asked, so the services' decisions can be read off it. */
    private static final class FakeStatementRepository implements EmployeeStatementRepository {

        private String lastKind;
        private BigDecimal lastAmount;
        private int lastUserId;
        private Integer runOfEntry;
        private int deletedEntryId;

        @Override
        public List<EmployeeStatementRow> page(EmployeeStatementFilter filter) {
            return new ArrayList<>();
        }

        @Override
        public EmployeeStatementSummary summarize(EmployeeStatementFilter filter) {
            return EmployeeStatementSummary.EMPTY;
        }

        @Override
        public LocalDate earliestMovement(int employeeId) {
            return FROM;
        }

        @Override
        public BigDecimal currentBalance(int employeeId) {
            return new BigDecimal("350.00");
        }

        @Override
        public List<EmployeeStatementUserOption> usersWhoEntered(int employeeId) {
            return List.of();
        }

        @Override
        public int insertLedgerEntry(int employeeId, LocalDate date, String kind, BigDecimal amount,
                                     String notes, int userId) {
            lastKind = kind;
            lastAmount = amount;
            lastUserId = userId;
            return 11;
        }

        @Override
        public Integer ledgerRunOf(int employeeId, int entryId) {
            return runOfEntry;
        }

        @Override
        public int deleteLedgerEntry(int employeeId, int entryId) {
            deletedEntryId = entryId;
            return 1;
        }

        @Override
        public int insertPurpose(int expenseId, String purpose, Integer payrollRunId, int userId) {
            return 1;
        }
    }

    @Test
    @DisplayName("the statement's page asks for one row more than it shows, to know there is another")
    void pagingReadsOneExtra() {
        assertEquals(51, filter().queryLimit());
        assertEquals(0, filter().offset());
        assertEquals(50, filter().onPage(1).offset());
        assertNull(filter().kindList(), "no kinds chosen adds no condition at all");
    }
}
