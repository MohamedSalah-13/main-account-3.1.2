package com.hamza.account.features.employee.attendance;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the attendance and leave services decide before they reach a database.
 * <p>
 * <b>The session is never user 1</b> - {@code isSystemAdministrator()} bypasses every
 * permission. What is not covered here and is written rather than implied: the transactions.
 * A grid save that fails halfway, and an approval whose days fail after the decision has
 * moved, both need a real transaction and are proven against MySQL instead.
 */
class AttendanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);

    private final FakeAttendanceRepository repository = new FakeAttendanceRepository();
    private final AttendanceService attendance = new AttendanceService(repository);
    private final LeaveService leave = new LeaveService(repository);

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    @DisplayName("reading the grid needs attendance.show")
    void readingIsGuarded() {
        signInWith(AppPermissions.EMPLOYEE_SHOW);
        assertThrows(BusinessRuleException.class,
                () -> attendance.daysBetween(DAY, DAY));
        assertThrows(BusinessRuleException.class, () -> attendance.leaveTypes());
    }

    @Test
    @DisplayName("recording a day needs its own key - seeing the grid does not grant it")
    void recordingIsGuarded() {
        signInWith(AppPermissions.ATTENDANCE_SHOW);
        assertThrows(BusinessRuleException.class,
                () -> attendance.recordDay(entry(AttendanceStatus.PRESENT, "8", null)));
    }

    @Test
    @DisplayName("deciding on leave needs its own key - asking for it does not grant it")
    void decidingIsGuarded() {
        signInWith(AppPermissions.LEAVE_REQUEST, AppPermissions.ATTENDANCE_SHOW);
        assertThrows(BusinessRuleException.class,
                () -> leave.decide(1, LeaveStatus.APPROVED, null),
                "asking for a day off is not granting one");
    }

    // ---- the day's own rules -------------------------------------------------------------------

    @Test
    @DisplayName("a leave day must name a type, and no other day may")
    void theLeaveTypeRule() {
        assertEquals("attendance.error.leave.type.required",
                assertThrows(UserValidationException.class,
                        () -> entry(AttendanceStatus.LEAVE, null, null)).getMessage());

        assertEquals("attendance.error.leave.type.unexpected",
                assertThrows(UserValidationException.class,
                        () -> entry(AttendanceStatus.PRESENT, "8", 3)).getMessage());
    }

    @Test
    @DisplayName("a day nobody worked carries no hours, whatever the grid left in the cell")
    void hoursAreClearedOnANonWorkingDay() throws Exception {
        assertEquals(BigDecimal.ZERO, entry(AttendanceStatus.WEEKEND, "8", null).hours());
        assertEquals(BigDecimal.ZERO, entry(AttendanceStatus.ABSENT, "8", null).hours());
        assertEquals(new BigDecimal("8"), entry(AttendanceStatus.PRESENT, "8", null).hours());
    }

    @Test
    @DisplayName("more than a day's worth of hours is refused")
    void hoursAreBounded() {
        assertEquals("attendance.error.hours",
                assertThrows(UserValidationException.class,
                        () -> entry(AttendanceStatus.PRESENT, "25", null)).getMessage());
        assertEquals("attendance.error.hours",
                assertThrows(UserValidationException.class,
                        () -> entry(AttendanceStatus.PRESENT, "-1", null)).getMessage());
    }

    @Test
    @DisplayName("a day before the hire date is refused by the service, not by the screen")
    void beforeTheHireDate() throws Exception {
        signInWith(AppPermissions.ATTENDANCE_RECORD);
        repository.hired = LocalDate.of(2026, 9, 20);

        assertEquals("attendance.error.before.hire",
                assertThrows(UserValidationException.class,
                        () -> attendance.recordDay(entry(AttendanceStatus.ABSENT, null, null)))
                        .getMessage(),
                "otherwise the payroll is fed an absence for a month the employee was not there");
    }

    @Test
    @DisplayName("a day after the last working day is refused too")
    void afterTheEndDate() throws Exception {
        signInWith(AppPermissions.ATTENDANCE_RECORD);
        repository.hired = LocalDate.of(2020, 1, 1);
        repository.ended = LocalDate.of(2026, 9, 10);

        assertEquals("attendance.error.after.end",
                assertThrows(UserValidationException.class,
                        () -> attendance.recordDay(entry(AttendanceStatus.ABSENT, null, null)))
                        .getMessage());
    }

    @Test
    @DisplayName("and a day inside employment is written")
    void insideEmployment() throws Exception {
        signInWith(AppPermissions.ATTENDANCE_RECORD);
        repository.hired = LocalDate.of(2020, 1, 1);

        attendance.recordDay(entry(AttendanceStatus.PRESENT, "8", null));

        assertEquals("PRESENT", repository.lastStatus);
        assertEquals(9, repository.lastUserId, "who recorded it, not user 1");
    }

    // ---- leave ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a request with the dates the wrong way round is refused")
    void backwardsDates() {
        signInWith(AppPermissions.LEAVE_REQUEST);
        assertEquals("leave.error.range",
                assertThrows(UserValidationException.class,
                        () -> leave.request(7, 1, DAY, DAY.minusDays(1), null)).getMessage());
    }

    @Test
    @DisplayName("a limit of zero means no limit written down, never none allowed")
    void zeroLimitAllowsEverything() throws Exception {
        signInWith(AppPermissions.LEAVE_REQUEST);
        repository.hired = LocalDate.of(2020, 1, 1);
        repository.types = List.of(new LeaveType(1, "سنوية", true, 0, true, null));

        leave.request(7, 1, DAY, DAY.plusDays(40), null);

        assertEquals(1, repository.insertedRequests,
                "V60 seeds zeros, so a real limit refusing everybody would be the upgrade's doing");
    }

    @Test
    @DisplayName("a request past the type's annual limit is refused")
    void pastTheAnnualLimit() {
        signInWith(AppPermissions.LEAVE_REQUEST);
        repository.hired = LocalDate.of(2020, 1, 1);
        repository.types = List.of(new LeaveType(1, "سنوية", true, 21, true, null));
        repository.approvedDays = 20;

        assertEquals("leave.error.limit",
                assertThrows(UserValidationException.class,
                        () -> leave.request(7, 1, DAY, DAY.plusDays(1), null)).getMessage(),
                "20 already taken plus 2 asked for is 22 against a limit of 21");
    }

    @Test
    @DisplayName("approving writes one attendance day per day of the range")
    void approvalMarksTheGrid() throws Exception {
        signInWith(AppPermissions.LEAVE_APPROVE, AppPermissions.ATTENDANCE_SHOW);
        LeaveRequest request = pendingRequest(DAY, DAY.plusDays(2));

        leave.decideWithin(1, LeaveStatus.APPROVED, "ok", request);

        assertEquals(3, repository.upserts.size(),
                "an approved request the payroll never reads would mark the employee absent "
                        + "for days the company granted");
        assertTrue(repository.upserts.stream().allMatch(day -> day.equals("LEAVE")));
    }

    @Test
    @DisplayName("rejecting writes no day at all")
    void rejectionMarksNothing() throws Exception {
        signInWith(AppPermissions.LEAVE_APPROVE, AppPermissions.ATTENDANCE_SHOW);

        leave.decideWithin(1, LeaveStatus.REJECTED, "no", pendingRequest(DAY, DAY.plusDays(2)));

        assertTrue(repository.upserts.isEmpty());
    }

    @Test
    @DisplayName("a second decision finds the row already moved and writes nothing")
    void decidingIsARaceTheDatabaseSettles() {
        signInWith(AppPermissions.LEAVE_APPROVE, AppPermissions.ATTENDANCE_SHOW);
        repository.decideResult = 0;

        assertEquals("leave.error.decided",
                assertThrows(UserValidationException.class,
                        () -> leave.decideWithin(1, LeaveStatus.APPROVED, null,
                                pendingRequest(DAY, DAY.plusDays(2)))).getMessage());
        assertTrue(repository.upserts.isEmpty());
    }

    @Test
    @DisplayName("a request is one day when it starts and ends on the same day, not zero")
    void oneDayIsOneDay() {
        assertEquals(1, pendingRequest(DAY, DAY).days());
        assertEquals(3, pendingRequest(DAY, DAY.plusDays(2)).days());
    }

    // ---- fixtures --------------------------------------------------------------------------

    private static AttendanceEntry entry(AttendanceStatus status, String hours, Integer typeId)
            throws UserValidationException {
        return AttendanceEntry.parse(7, DAY, status,
                hours == null ? null : new BigDecimal(hours), typeId, null);
    }

    private static LeaveRequest pendingRequest(LocalDate from, LocalDate to) {
        return new LeaveRequest(1, 7, "سها", 1, "سنوية", true, from, to,
                LeaveStatus.PENDING, null, null, null, null, null);
    }

    private static final class FakeAttendanceRepository implements AttendanceRepository {

        private LocalDate hired = LocalDate.of(2020, 1, 1);
        private LocalDate ended;
        private String lastStatus;
        private int lastUserId;
        private int insertedRequests;
        private int approvedDays;
        private int decideResult = 1;
        private List<LeaveType> types = List.of(new LeaveType(1, "سنوية", true, 0, true, null));
        private final List<String> upserts = new ArrayList<>();

        @Override
        public List<AttendanceDay> daysBetween(LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public List<AttendanceDay> daysOf(int employeeId, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public int upsertDay(int employeeId, LocalDate date, String status, BigDecimal hours,
                             Integer leaveTypeId, String notes, int userId) {
            lastStatus = status;
            lastUserId = userId;
            upserts.add(status);
            return 1;
        }

        @Override
        public int deleteDay(int employeeId, LocalDate date) {
            return 1;
        }

        @Override
        public List<LeaveType> leaveTypes() {
            return types;
        }

        @Override
        public int insertLeaveType(String name, boolean paid, int annualLimit, boolean active,
                                   String notes, int userId) {
            return 1;
        }

        @Override
        public int updateLeaveType(int id, String name, boolean paid, int annualLimit,
                                   boolean active, String notes) {
            return 1;
        }

        @Override
        public List<LeaveRequest> requests(LeaveStatus status, int limit) {
            return List.of();
        }

        @Override
        public Optional<LeaveRequest> findRequest(int requestId) {
            return Optional.of(pendingRequest(DAY, DAY.plusDays(2)));
        }

        @Override
        public int insertRequest(int employeeId, int leaveTypeId, LocalDate from, LocalDate to,
                                 String reason, int userId) {
            insertedRequests++;
            return insertedRequests;
        }

        @Override
        public int decideRequest(int requestId, LeaveStatus decision, String note, int userId) {
            return decideResult;
        }

        @Override
        public int approvedDaysInYear(int employeeId, int leaveTypeId, int year,
                                      int excludingRequestId) {
            return approvedDays;
        }

        @Override
        public LocalDate[] employmentDates(int employeeId) {
            return new LocalDate[]{hired, ended};
        }
    }
}
