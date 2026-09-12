package com.hamza.account.features.employee.attendance;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Asking for leave, and deciding on it.
 *
 * <h2>Requesting and approving are different permissions, and that is the point</h2>
 * Anybody who can see the employees may record a request; deciding on one is its own key. The
 * same separation V55 drew between collecting money and deciding a customer owes more, and V59
 * between computing a payroll and approving it.
 *
 * <h2>Approving writes the days onto the grid</h2>
 * Otherwise an approved request would be a piece of paper the payroll never reads, and the
 * employee would be marked absent for days the company granted. The days and the decision are
 * written in one transaction: a request that says APPROVED but whose days are not on the grid
 * is worse than one that was never decided.
 *
 * <h2>The annual limit is the type's number, not a constant</h2>
 * {@code leave_type.annual_limit}, where zero means "no limit written down". A number in Java
 * would be a number belonging to one company.
 */
public final class LeaveService {

    private final AttendanceRepository repository;

    public LeaveService() {
        this(new JdbcAttendanceRepository());
    }

    public LeaveService(AttendanceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<LeaveRequest> requests(LeaveStatus status, int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ATTENDANCE_SHOW);
        return repository.requests(status, Math.max(1, limit));
    }

    /**
     * Records a request. It grants nothing and writes no day.
     *
     * @throws UserValidationException if the dates are the wrong way round, fall outside the
     *                                 employee's employment, or would take the employee past
     *                                 the type's annual limit
     */
    public int request(int employeeId, int leaveTypeId, LocalDate from, LocalDate to,
                       String reason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.LEAVE_REQUEST);
        requireDates(from, to);
        requireWithinEmployment(employeeId, from, to);
        requireWithinAnnualLimit(employeeId, leaveTypeId, from, to, 0);
        return repository.insertRequest(employeeId, leaveTypeId, from, to, reason, currentUserId());
    }

    /**
     * Approves a request and marks its days on the grid, or rejects it and marks nothing.
     * <p>
     * The decision carries a pending-only condition in SQL, so two people deciding at once
     * produce one decision and one refusal rather than two sets of attendance rows.
     */
    public int decide(int requestId, LeaveStatus decision, String note) throws DaoException {
        AuthorizationGuard.require(AppPermissions.LEAVE_APPROVE);
        if (decision == null || decision.isOpen()) {
            throw new UserValidationException("leave.error.decision");
        }
        LeaveRequest request = repository.findRequest(requestId)
                .orElseThrow(() -> new UserValidationException("leave.error.missing"));
        if (!request.status().isOpen()) {
            throw new UserValidationException("leave.error.decided");
        }
        if (decision == LeaveStatus.APPROVED) {
            requireWithinAnnualLimit(request.employeeId(), request.leaveTypeId(), request.from(),
                    request.to(), requestId);
        }
        return TransactionTemplate.execute(() -> decideWithin(requestId, decision, note, request));
    }

    /**
     * The decision and the days, as one unit - called only from inside a transaction.
     * <p>
     * Separate so what it decides can be tested without a database: that a rejection writes no
     * day, that an approval writes one per day of the range, and that nothing is written when
     * the decision loses its race.
     */
    int decideWithin(int requestId, LeaveStatus decision, String note, LeaveRequest request)
            throws DaoException {
        int moved = repository.decideRequest(requestId, decision, note, currentUserId());
        if (moved != 1) {
            throw new UserValidationException("leave.error.decided");
        }
        if (decision == LeaveStatus.APPROVED) {
            for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
                repository.upsertDay(request.employeeId(), day, AttendanceStatus.LEAVE.name(),
                        BigDecimal.ZERO, request.leaveTypeId(), note, currentUserId());
            }
        }
        return moved;
    }

    // ---- the rules ---------------------------------------------------------------------------

    private static void requireDates(LocalDate from, LocalDate to) throws UserValidationException {
        if (from == null || to == null || to.isBefore(from)) {
            throw new UserValidationException("leave.error.range");
        }
    }

    private void requireWithinEmployment(int employeeId, LocalDate from, LocalDate to)
            throws DaoException {
        LocalDate[] employment = repository.employmentDates(employeeId);
        if (employment == null) {
            throw new UserValidationException("attendance.error.employee");
        }
        if (employment[0] != null && from.isBefore(employment[0])) {
            throw new UserValidationException("attendance.error.before.hire");
        }
        if (employment[1] != null && to.isAfter(employment[1])) {
            throw new UserValidationException("attendance.error.after.end");
        }
    }

    /**
     * Zero means "no limit written down", never "none allowed" - so a database upgraded with
     * V60's seeded zeros refuses nobody.
     */
    private void requireWithinAnnualLimit(int employeeId, int leaveTypeId, LocalDate from,
                                          LocalDate to, int excludingRequestId)
            throws DaoException {
        LeaveType type = repository.leaveTypes().stream()
                .filter(candidate -> candidate.id() == leaveTypeId)
                .findFirst()
                .orElseThrow(() -> new UserValidationException("leave.error.type"));
        if (type.annualLimit() <= 0) {
            return;
        }
        int wanted = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        int already = repository.approvedDaysInYear(employeeId, leaveTypeId, from.getYear(),
                excludingRequestId);
        if (already + wanted > type.annualLimit()) {
            throw new UserValidationException("leave.error.limit");
        }
    }

    private static int currentUserId() {
        return CurrentUser.getOrNull() == null ? 1 : CurrentUser.get().getId();
    }
}
