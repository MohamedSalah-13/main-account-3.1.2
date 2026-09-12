package com.hamza.account.features.employee;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.domain.Employees;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one place employees are read and written.
 * <p>
 * <b>What the salary permission means here.</b> {@code employees.show.salary} used to hide a
 * column: the screen read every salary in the shop and then called {@code setVisible(false)} on
 * the fifth column, by index. So the figures were in the memory of a screen opened by somebody
 * who may not see them, and reordering one line in the column list would have shown them. The
 * permission is answered once, in {@link #salaryVisible()}, and what it decides is whether the
 * columns are selected at all - which is the difference between a hint and enforcement that the
 * {@code AuthorizationGuard} javadoc spells out.
 * <p>
 * It follows that <b>filtering on a rate requires the same permission</b>: a bound you can move
 * is a way of reading the figure it filters on, one comparison at a time, and a list that
 * quietly dropped the bound would answer the wrong question instead.
 * <p>
 * <b>The three methods returning {@code Employees} are a seam, not a model choice.</b>
 * {@code Total_Sales}, {@code Total_Sales_Re} and {@code ExpensesDetails} hold their delegate as
 * that class, so an invoice cannot be saved without one; they carry an id and a name and
 * nothing else, and no salary is read to produce them. They die with the single {@code Document}
 * model {@code CLAUDE.md} describes, and not before.
 */
public final class EmployeeService {

    /** As elsewhere: an extract wide enough to print, narrow enough to hold. */
    public static final int PRINT_LIMIT = 10_000;

    private final EmployeeRepository repository;
    private final JobRepository jobs;

    public EmployeeService() {
        this(new JdbcEmployeeRepository(), new JdbcJobRepository());
    }

    public EmployeeService(EmployeeRepository repository, JobRepository jobs) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
    }

    // ---- reading ------------------------------------------------------------------------

    /**
     * One page, with the figures for the whole filtered set.
     * <p>
     * One row more than the page holds is fetched, and that row is what answers "is there
     * another page" - so there is no second count to fall out of step with the conditions of
     * the page itself.
     */
    public EmployeePage search(EmployeeFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        boolean salary = requireSalaryForRateBounds(filter);
        List<Employee> fetched = repository.search(filter, salary);
        boolean hasNext = fetched.size() > filter.pageSize();
        List<Employee> rows = hasNext ? fetched.subList(0, filter.pageSize()) : fetched;
        return new EmployeePage(rows, repository.summarize(filter, salary), filter.page(),
                filter.page() > 0, hasNext, false);
    }

    /**
     * Every matching employee, for printing and for export.
     * <p>
     * A query of its own with the same filter, so a printed or exported list is the list on
     * screen - all of it, rather than the page that happens to be showing or the rows somebody
     * ticked.
     */
    public EmployeePage forPrint(EmployeeFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        boolean salary = requireSalaryForRateBounds(filter);
        EmployeeFilter printable = filter.firstPageWithSize(PRINT_LIMIT);
        List<Employee> fetched = repository.search(printable, salary);
        boolean truncated = fetched.size() > PRINT_LIMIT;
        List<Employee> rows = truncated ? fetched.subList(0, PRINT_LIMIT) : fetched;
        return new EmployeePage(rows, repository.summarize(printable, salary), 0, false, false,
                truncated);
    }

    public Employee find(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        return repository.find(id, salaryVisible());
    }

    public byte[] photo(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        return repository.photo(id);
    }

    /** The whole dated history of what this employee has been paid. All of it is a salary. */
    public List<EmployeeCompensation> salaryHistory(int employeeId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        AuthorizationGuard.require(AppPermissions.EMPLOYEES_SHOW_SALARY);
        return repository.compensationHistory(employeeId);
    }

    /** Whether this reader may see what anybody is paid - a hint, for hiding a column or a tab. */
    public boolean salaryVisible() {
        return AuthorizationGuard.isGranted(AppPermissions.EMPLOYEES_SHOW_SALARY);
    }

    // ---- writing ------------------------------------------------------------------------

    /**
     * Creates the employee and the first dated rate, in one transaction.
     * <p>
     * The two belong together: an employee with no compensation row is one whose pay nothing
     * can answer for, and half a save is worse than a refused one.
     *
     * @return the generated code
     */
    public int create(EmployeeDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_CREATE);
        requireNameFree(draft.name(), 0);
        return TransactionTemplate.execute(() -> {
            int id = repository.insert(draft, currentUserId());
            repository.saveCompensation(id, draft.hireDate(), draft.salaryKind(), draft.rate(),
                    null, currentUserId());
            return id;
        });
    }

    /**
     * The form, minus the things the form does not own.
     * <p>
     * {@code is_active}, the picture and {@code user_id} are written by their own statements and
     * never carried through here - see {@link EmployeeQuery#UPDATE_SQL}. The salary is a dated
     * row, and this method will move it only while {@link SalaryChangeGuard} still calls that a
     * correction.
     */
    public int update(EmployeeDraft draft) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_UPDATE);
        requireNameFree(draft.name(), draft.id());
        return TransactionTemplate.execute(() -> {
            int rows = repository.update(draft);
            correctHireRateIfAsked(draft);
            return rows;
        });
    }

    /**
     * Records what this employee is paid from a given day.
     * <p>
     * This is the road the refusal in {@link SalaryChangeGuard} points at, and it exists before
     * the refusal can be met - {@code opening.correction.customers} told users for months to
     * record a movement on an account that had no writer, which is the mistake not to repeat.
     */
    public int changeSalary(int employeeId, LocalDate effectiveFrom, SalaryKind kind,
                            BigDecimal rate, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SALARY_CHANGE);
        if (effectiveFrom == null) {
            throw new UserValidationException("employee.error.salary.date");
        }
        if (rate == null || rate.signum() < 0) {
            throw new UserValidationException("employee.error.salary.negative");
        }
        return TransactionTemplate.execute(() -> {
            int rows = repository.saveCompensation(employeeId, effectiveFrom,
                    kind == null ? SalaryKind.MONTHLY : kind, rate, notes, currentUserId());
            syncHireRate(employeeId);
            return rows;
        });
    }

    /**
     * Removes one dated rate. The earliest may not be removed: it is what the employee was hired
     * at, it is the figure {@code employees.salary} mirrors, and an employee with no rate at all
     * is one nothing can calculate.
     */
    public int removeSalaryChange(int employeeId, int compensationId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SALARY_CHANGE);
        return TransactionTemplate.execute(() -> {
            List<EmployeeCompensation> history = repository.compensationHistory(employeeId);
            if (history.size() <= 1) {
                throw new UserValidationException("employee.error.salary.last");
            }
            if (history.get(history.size() - 1).id() == compensationId) {
                throw new UserValidationException("employee.error.salary.first");
            }
            int rows = repository.deleteCompensation(employeeId, compensationId);
            syncHireRate(employeeId);
            return rows;
        });
    }

    /**
     * Stops an employee, or starts them again.
     * <p>
     * There is no delete for somebody who has worked here: {@code DeleteRegistry} refuses one
     * named on an invoice or an expense - rightly, their history is not deleted with them - and
     * {@code employees.column_name} is unique, so the name could never be issued again either.
     * The same answer {@code UsersService.updateActive} gives, for the same reason.
     */
    public int setActive(int employeeId, boolean active) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_UPDATE);
        return repository.setActive(employeeId, active);
    }

    public int updatePhoto(int employeeId, byte[] photo) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_UPDATE);
        return repository.updatePhoto(employeeId, photo);
    }

    /** Only ever possible for somebody nothing points at - a row entered by mistake. */
    public int delete(int employeeId) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.EMPLOYEES, employeeId, repository::delete)
                .rowsOrThrow();
    }

    // ---- jobs ---------------------------------------------------------------------------

    public List<Job> jobs(EmployeeScope scope) throws DaoException {
        AuthorizationGuard.require(AppPermissions.JOB_SHOW);
        return jobs.jobs(scope == EmployeeScope.ACTIVE_ONLY);
    }

    public int saveJob(Job job) throws DaoException {
        AuthorizationGuard.require(job.id() == 0 ? AppPermissions.JOB_CREATE : AppPermissions.JOB_UPDATE);
        String name = job.name() == null ? "" : job.name().strip();
        if (name.isEmpty()) {
            throw new UserValidationException("job.error.name");
        }
        if (name.codePointCount(0, name.length()) > 50) {
            throw new UserValidationException("job.error.name.length");
        }
        if (jobs.nameTaken(name, job.id())) {
            throw new UserValidationException("job.error.name.taken");
        }
        Job clean = new Job(job.id(), name, job.delegate(), job.active(), job.defaultSalary(),
                job.notes());
        return job.id() == 0 ? jobs.insert(clean, currentUserId()) : jobs.update(clean);
    }

    public int deleteJob(int jobId) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.JOBS, jobId, jobs::delete)
                .rowsOrThrow();
    }

    // ---- lookups, and the legacy seam ----------------------------------------------------

    /**
     * Names for a combo box, and nothing else read to produce them.
     * <p>
     * Deliberately unguarded: a cashier recording a wage payment picks a name, and that is not a
     * request to read the payroll. The guard belongs on the methods handing over figures.
     */
    public List<String> employeeNames(EmployeeScope scope) throws DaoException {
        return repository.names(scope, false);
    }

    public List<String> delegateNames(EmployeeScope scope) throws DaoException {
        return repository.names(scope, true);
    }

    public List<EmployeeRef> delegateRefs(EmployeeScope scope) throws DaoException {
        return repository.refs(scope, true);
    }

    /** The delegates as the invoice models still need them: an id and a name, no salary. */
    public List<Employees> delegates(EmployeeScope scope) throws DaoException {
        List<Employees> legacy = new ArrayList<>();
        for (EmployeeRef ref : repository.refs(scope, true)) {
            legacy.add(new Employees(ref.id(), ref.name()));
        }
        return legacy;
    }

    /** Resolves the delegate an invoice names. {@code null} when no employee carries that name. */
    public Employees delegateByName(String name) throws DaoException {
        return legacy(repository.refByName(name));
    }

    public Employees delegateById(int id) throws DaoException {
        return legacy(repository.refById(id));
    }

    private static Employees legacy(EmployeeRef ref) {
        return ref == null ? null : new Employees(ref.id(), ref.name());
    }

    // ---- internals ----------------------------------------------------------------------

    /**
     * Answers whether salaries may be read, and refuses a filter that asks about one when they
     * may not - with the ordinary permission refusal rather than a message of its own, because
     * that is exactly what it is.
     */
    private boolean requireSalaryForRateBounds(EmployeeFilter filter) throws DaoException {
        if (filter.filtersOnRate()) {
            AuthorizationGuard.require(AppPermissions.EMPLOYEES_SHOW_SALARY);
            return true;
        }
        return salaryVisible();
    }

    /**
     * A duplicate name is refused with a sentence a person can act on rather than with the
     * unique key's own error. The index is still the decision - two people saving the same name
     * both pass this check and the second is refused by {@code employees_pk2} - this is the
     * courtesy that makes the ordinary case readable, exactly as {@code MasterDataService} puts
     * it - and it is asked before the transaction is opened, because a courtesy that needs a
     * transaction is one nothing can check without a database.
     */
    private void requireNameFree(String name, int exceptId) throws DaoException {
        if (repository.nameTaken(name, exceptId)) {
            throw new UserValidationException("employee.error.name.taken");
        }
    }

    /**
     * The salary box on the form, when it has been moved.
     * <p>
     * While there is one dated rate, it and {@code employees.salary} say the same single thing,
     * so a correction typed the same afternoon corrects both. Once there are two, the history
     * has begun and rewriting the first one changes what a past month was calculated from -
     * refused, and the message names the road: record a dated change.
     */
    private void correctHireRateIfAsked(EmployeeDraft draft) throws DaoException {
        List<EmployeeCompensation> history = repository.compensationHistory(draft.id());
        EmployeeCompensation current = history.isEmpty() ? null : history.get(0);
        if (current != null
                && !SalaryChangeGuard.isChanged(current.salaryKind(), current.rate(),
                draft.salaryKind(), draft.rate())) {
            return;
        }
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SALARY_CHANGE);
        SalaryChangeGuard.requireCorrectable(history.size());
        LocalDate effectiveFrom = current == null ? draft.hireDate() : current.effectiveFrom();
        repository.saveCompensation(draft.id(), effectiveFrom, draft.salaryKind(), draft.rate(),
                null, currentUserId());
        syncHireRate(draft.id());
    }

    /**
     * Keeps {@code employees.salary} equal to the earliest dated rate.
     * <p>
     * That column means one thing - what this employee was hired at - and this is the only place
     * that writes it, so it cannot come to mean two. Without the invariant it would be whatever
     * was last typed into a form, which is how it came to mean nothing definite in the first
     * place.
     */
    private void syncHireRate(int employeeId) throws DaoException {
        List<EmployeeCompensation> history = repository.compensationHistory(employeeId);
        if (history.isEmpty()) {
            return;
        }
        repository.updateHireRate(employeeId, history.get(history.size() - 1).rate());
    }

    private static int currentUserId() {
        var user = CurrentUser.getOrNull();
        return user == null ? 1 : user.getId();
    }
}
