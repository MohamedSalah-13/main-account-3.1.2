package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the service decides before it reaches a database.
 * <p>
 * <b>The session is never user 1.</b> {@code UserSessionContext.isSystemAdministrator()} is
 * {@code currentUserId() == 1} and bypasses every permission, so a test signed in as user 1 proves
 * nothing about authorization - it is the trap {@code ItemGroupMoveDatabaseAcceptanceTest} found in
 * itself, and it is not specific to that class.
 * <p>
 * What is <b>not</b> covered here, and is written down rather than implied: everything inside
 * {@code TransactionTemplate} - creating an employee with their first dated rate, correcting that
 * rate, and the invariant that {@code employees.salary} mirrors the earliest row. Those open a real
 * transaction, so the only honest proof is a run against MySQL.
 */
class EmployeeServiceTest {

    private final FakeEmployees employees = new FakeEmployees();
    private final FakeJobs jobs = new FakeJobs();
    private final EmployeeService service = new EmployeeService(employees, jobs);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @Test
    @DisplayName("the salary columns are not selected for a reader without the permission")
    void salaryIsNotFetched() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_SHOW);

        service.search(EmployeeFilter.all());

        assertFalse(employees.lastSalaryVisible,
                "hiding the column afterwards is what this replaced: the figure crossed the "
                        + "connection either way");
        assertFalse(service.salaryVisible());
    }

    @Test
    @DisplayName("and are, for a reader with it")
    void salaryIsFetchedWhenGranted() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_SHOW, AppPermissions.EMPLOYEES_SHOW_SALARY);

        service.search(EmployeeFilter.all());

        assertTrue(employees.lastSalaryVisible);
    }

    @Test
    @DisplayName("filtering on a salary needs the salary permission, because a bound can be moved")
    void rateFilterIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);
        EmployeeFilter narrowed = EmployeeFilter.all()
                .withRateBounds(new BigDecimal("3000"), new BigDecimal("3100"));

        assertThrows(BusinessRuleException.class, () -> service.search(narrowed),
                "a filter you can move is a way of reading the figure it filters on, one "
                        + "comparison at a time");
    }

    @Test
    @DisplayName("opening the list at all needs the employees permission")
    void listIsGuarded() {
        signInWith();
        assertThrows(BusinessRuleException.class, () -> service.search(EmployeeFilter.all()));
        assertThrows(BusinessRuleException.class, () -> service.find(1));
    }

    @Test
    @DisplayName("the whole salary history is a salary, so it needs both permissions")
    void historyIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.salaryHistory(1));
    }

    @Test
    @DisplayName("a name for a dropdown is not a salary, and is deliberately unguarded")
    void namesAreOpen() throws Exception {
        signInWith();
        assertEquals(List.of("عمر"), service.employeeNames(EmployeeScope.ACTIVE_ONLY));
        assertEquals(1, service.delegates(EmployeeScope.ACTIVE_ONLY).size());
    }

    @Test
    @DisplayName("a delegate handed to an invoice carries an id and a name, and no figure")
    void delegatesCarryNoSalary() throws Exception {
        signInWith();
        var delegate = service.delegateByName("عمر");
        assertEquals(7, delegate.getId());
        assertEquals("عمر", delegate.getName());
        assertEquals(0.0, delegate.getSalary(), 0.0001,
                "the projection this replaced pulled every delegate's pay across the connection");
    }

    @Test
    @DisplayName("a name already taken is refused with a key, before the unique index refuses it")
    void duplicateName() {
        signInWith(AppPermissions.EMPLOYEE_UPDATE);
        employees.taken = true;

        // The courtesy check, not the decision: the index is still what makes it true.
        assertEquals("employee.error.name.taken",
                assertThrows(UserValidationException.class,
                        () -> service.update(draft())).getMessage());
    }

    @Test
    @DisplayName("stopping an employee needs the update permission, and writes only that column")
    void setActive() throws Exception {
        signInWith(AppPermissions.EMPLOYEE_UPDATE);
        assertEquals(1, service.setActive(7, false));
        assertEquals(Boolean.FALSE, employees.lastActive);

        signInWith();
        assertThrows(BusinessRuleException.class, () -> service.setActive(7, true));
    }

    @Test
    @DisplayName("a job needs a name, and one that is taken is refused before the index refuses it")
    void jobValidation() {
        signInWith(AppPermissions.JOB_CREATE);
        assertEquals("job.error.name", assertThrows(UserValidationException.class,
                () -> service.saveJob(new Job(0, "  ", false, true, null, null))).getMessage());

        jobs.taken = true;
        assertEquals("job.error.name.taken", assertThrows(UserValidationException.class,
                () -> service.saveJob(new Job(0, "مندوب", true, true, null, null))).getMessage());
    }

    @Test
    @DisplayName("reading the jobs needs its own permission, which V57 grants to whoever had employee.show")
    void jobsAreGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);
        assertThrows(BusinessRuleException.class, () -> service.jobs(EmployeeScope.EVERYONE));
    }

    private static EmployeeDraft draft() throws UserValidationException {
        return EmployeeDraft.parse(7, "عمر", 1, null, LocalDate.of(2026, 1, 1), null,
                EmploymentType.FULL_TIME, "", "", "", "", "", null, SalaryKind.MONTHLY,
                new BigDecimal("3000"));
    }

    /** A repository that records what it was asked, so the service's decisions can be read off it. */
    private static final class FakeEmployees implements EmployeeRepository {

        private boolean lastSalaryVisible;
        private Boolean lastActive;
        private boolean taken;

        @Override
        public List<Employee> search(EmployeeFilter filter, boolean salaryVisible) {
            lastSalaryVisible = salaryVisible;
            return new ArrayList<>();
        }

        @Override
        public EmployeeSummary summarize(EmployeeFilter filter, boolean salaryVisible) {
            return EmployeeSummary.EMPTY;
        }

        @Override
        public Employee find(int id, boolean salaryVisible) {
            lastSalaryVisible = salaryVisible;
            return null;
        }

        @Override
        public byte[] photo(int id) {
            return null;
        }

        @Override
        public List<String> names(EmployeeScope scope, boolean delegatesOnly) {
            return List.of("عمر");
        }

        @Override
        public List<EmployeeRef> refs(EmployeeScope scope, boolean delegatesOnly) {
            return List.of(new EmployeeRef(7, "عمر"));
        }

        @Override
        public EmployeeRef refByName(String name) {
            return new EmployeeRef(7, name);
        }

        @Override
        public EmployeeRef refById(int id) {
            return new EmployeeRef(id, "عمر");
        }

        @Override
        public boolean nameTaken(String name, int exceptId) {
            return taken;
        }

        @Override
        public int insert(EmployeeDraft draft, int userId) {
            return 7;
        }

        @Override
        public int update(EmployeeDraft draft) {
            return 1;
        }

        @Override
        public int setActive(int id, boolean active) {
            lastActive = active;
            return 1;
        }

        @Override
        public int updatePhoto(int id, byte[] photo) {
            return 1;
        }

        @Override
        public int delete(int id) {
            return 1;
        }

        @Override
        public List<EmployeeCompensation> compensationHistory(int employeeId) {
            return List.of();
        }

        @Override
        public int compensationCount(int employeeId) {
            return 0;
        }

        @Override
        public int saveCompensation(int employeeId, LocalDate effectiveFrom, SalaryKind kind,
                                    BigDecimal rate, String notes, int userId) {
            return 1;
        }

        @Override
        public int deleteCompensation(int employeeId, int compensationId) {
            return 1;
        }

        @Override
        public int updateHireRate(int employeeId, BigDecimal rate) {
            return 1;
        }
    }

    private static final class FakeJobs implements JobRepository {

        private boolean taken;

        @Override
        public List<Job> jobs(boolean activeOnly) {
            return List.of(Job.of(1, "موظف"));
        }

        @Override
        public boolean nameTaken(String name, int exceptId) {
            return taken;
        }

        @Override
        public int insert(Job job, int userId) {
            return 2;
        }

        @Override
        public int update(Job job) {
            return 1;
        }

        @Override
        public int employeesHolding(int jobId) {
            return 0;
        }

        @Override
        public int delete(int id) throws DaoException {
            return 1;
        }
    }
}
