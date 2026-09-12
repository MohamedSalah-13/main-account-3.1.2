package com.hamza.account.features.employee.attendance;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** What the attendance needs of a database, named so the services can be tested without one. */
public interface AttendanceRepository {

    List<AttendanceDay> daysBetween(LocalDate from, LocalDate to) throws DaoException;

    List<AttendanceDay> daysOf(int employeeId, LocalDate from, LocalDate to) throws DaoException;

    int upsertDay(int employeeId, LocalDate date, String status, BigDecimal hours,
                  Integer leaveTypeId, String notes, int userId) throws DaoException;

    int deleteDay(int employeeId, LocalDate date) throws DaoException;

    List<LeaveType> leaveTypes() throws DaoException;

    int insertLeaveType(String name, boolean paid, int annualLimit, boolean active, String notes,
                        int userId) throws DaoException;

    int updateLeaveType(int id, String name, boolean paid, int annualLimit, boolean active,
                        String notes) throws DaoException;

    List<LeaveRequest> requests(LeaveStatus status, int limit) throws DaoException;

    Optional<LeaveRequest> findRequest(int requestId) throws DaoException;

    int insertRequest(int employeeId, int leaveTypeId, LocalDate from, LocalDate to, String reason,
                      int userId) throws DaoException;

    /** @return 1 when this caller decided it, 0 when somebody else got there first */
    int decideRequest(int requestId, LeaveStatus decision, String note, int userId)
            throws DaoException;

    /** Days of this type already approved for the employee in a year, excluding one request. */
    int approvedDaysInYear(int employeeId, int leaveTypeId, int year, int excludingRequestId)
            throws DaoException;

    /** The hire date and the last working day, so a day outside employment can be refused. */
    LocalDate[] employmentDates(int employeeId) throws DaoException;
}
