package com.hamza.account.features.employee;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the employee screens read and write, as one seam.
 * <p>
 * {@code salaryVisible} is threaded through the reads rather than decided inside them: the
 * permission is answered once, in {@link EmployeeService}, and what it decides is whether the
 * figure is <b>selected at all</b>. An implementation is free to be a mock - which is what
 * makes the service testable without a database.
 */
public interface EmployeeRepository {

    List<Employee> search(EmployeeFilter filter, boolean salaryVisible) throws DaoException;

    EmployeeSummary summarize(EmployeeFilter filter, boolean salaryVisible) throws DaoException;

    /** One employee, or {@code null} when no row carries that code. */
    Employee find(int id, boolean salaryVisible) throws DaoException;

    /** The picture, or {@code null}. Read on its own so a list never carries one. */
    byte[] photo(int id) throws DaoException;

    List<String> names(EmployeeScope scope, boolean delegatesOnly) throws DaoException;

    List<EmployeeRef> refs(EmployeeScope scope, boolean delegatesOnly) throws DaoException;

    EmployeeRef refByName(String name) throws DaoException;

    EmployeeRef refById(int id) throws DaoException;

    boolean nameTaken(String name, int exceptId) throws DaoException;

    /** The generated code, which the caller needs to write the first compensation row. */
    int insert(EmployeeDraft draft, int userId) throws DaoException;

    int update(EmployeeDraft draft) throws DaoException;

    int setActive(int id, boolean active) throws DaoException;

    int updatePhoto(int id, byte[] photo) throws DaoException;

    int delete(int id) throws DaoException;

    // ---- the dated salary ---------------------------------------------------------------

    List<EmployeeCompensation> compensationHistory(int employeeId) throws DaoException;

    int compensationCount(int employeeId) throws DaoException;

    /** Writes the rate for that day, replacing one already recorded for it. */
    int saveCompensation(int employeeId, LocalDate effectiveFrom, SalaryKind kind, BigDecimal rate,
                         String notes, int userId) throws DaoException;

    int deleteCompensation(int employeeId, int compensationId) throws DaoException;

    /** The hire figure on the employee row, correctable only while {@link SalaryChangeGuard} allows it. */
    int updateHireRate(int employeeId, BigDecimal rate) throws DaoException;
}
