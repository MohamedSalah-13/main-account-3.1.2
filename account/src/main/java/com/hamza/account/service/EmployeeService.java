package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.EmployeesDao;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.type.UsersType;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record EmployeeService(DaoFactory daoFactory) {

    /**
     * Every employee, salary included - so it asks permission first.
     * <p>
     * {@code Employees} carries {@code salary}, so any method returning one hands over
     * what everybody in the business is paid. This was open to any signed-in user. The
     * employees screen hid the salary <em>column</em> from anyone without
     * {@code employees.show.salary}, which is a UI hint and was never enforcement: the
     * figure was still read out of the database and handed to the caller, and anything
     * reaching the service another way got all of it.
     * <p>
     * {@code EMPLOYEE_SHOW} already exists and already decides whether the employees
     * screen opens, so nobody gains or loses access by this being checked where it
     * counts rather than only on a button.
     */
    public List<Employees> getEmployeesList() throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        return getEmployeesDao().loadAll();
    }

    /**
     * Names only, and deliberately not guarded.
     * <p>
     * It used to derive from {@link #getEmployeesList()}, which would now mean the
     * expenses screen needed {@code employee.show} to fill a combo box with names - a
     * cashier recording a wage payment is not asking to read the payroll. A name is not
     * a salary, so this reads the rows and keeps only the names, and the guard stays on
     * the method that hands over the figures.
     */
    public List<String> getEmployeeNames() throws DaoException {
        return getEmployeesDao().loadAll()
                .stream()
                .map(Employees::getName)
                .toList();
    }

    /**
     * The delegates, unguarded - an invoice needs one, and cashiers write invoices.
     * <p>
     * <b>This still hands over a salary</b>, because a delegate is an {@code Employees}
     * and the model carries the column. Guarding it would break the delegate combo on
     * every invoice screen, which is worse; the real fix is a projection carrying an id
     * and a name and nothing else, and that is a change to the model and its callers
     * rather than a line here. Recorded rather than quietly left:
     * {@link #getDelegateNames()} is what the invoice screens actually use, and it is
     * already only names.
     */
    public List<Employees> getDelegateList() throws DaoException {
        return getEmployeesDao().loadAllDelegate();
    }

    @NotNull
    private EmployeesDao getEmployeesDao() {
        return daoFactory.employeesDao();
    }

    public List<String> getDelegateNames() throws DaoException {
        return getDelegateList()
                .stream()
                .map(Employees::getName)
                .toList();
    }

    public Employees getDelegateByName(String name) throws DaoException {
        return getEmployeesDao().getDataByString(name);
    }

    public Employees getDelegateById(int id) throws DaoException {
        return getEmployeesDao().getDataById(id);
    }

    public int deleteEmployee(int id) throws DaoException {
        return DeletionService.shared()
                .delete(DeleteRegistry.EMPLOYEES, id, getEmployeesDao()::deleteById)
                .rowsOrThrow();
    }

    public int updateEmployee(int id, String name, LocalDate birth_date, LocalDate hire_date, double salary, String email, String tel, String address, UsersType job_id) throws DaoException {
        AuthorizationGuard.require(id == 0 ? AppPermissions.EMPLOYEE_CREATE : AppPermissions.EMPLOYEE_UPDATE);
        var employees = new Employees(id, name, birth_date, hire_date, salary, email, tel, address, job_id);
        if (id == 0)
            return getEmployeesDao().insert(employees);

        else return getEmployeesDao().update(employees);
    }

    /** The employees screen's search - same rows, same salary, same guard. */
    public List<Employees> getFilterEmployees(String searchText) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        return getEmployeesDao().getFilterEmployees(searchText);
    }

    /** The employees screen's paging - same rows, same salary, same guard. */
    public List<Employees> getProducts(int rowsPerPage, int offset) throws DaoException {
        AuthorizationGuard.require(AppPermissions.EMPLOYEE_SHOW);
        return getEmployeesDao().getProducts(rowsPerPage, offset);
    }

    public int getCountItems() {
        return getEmployeesDao().getCountItems();
    }
}
