package com.hamza.account.features.employee.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a month of days means to the payroll.
 * <p>
 * Every case here is one the collapse would get wrong in money: a rest day counted as worked
 * pays a wage for a day nobody worked, a rest day counted as missed deducts an entitlement,
 * and a paid leave counted as absence charges the employee for a day the company granted.
 */
class AttendanceSummaryTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static AttendanceDay day(int index, AttendanceStatus status, String hours,
                                     boolean leavePaid) {
        Integer typeId = status == AttendanceStatus.LEAVE ? 1 : null;
        return new AttendanceDay(index, 7, "سها", LocalDate.of(2026, 9, index), status,
                hours == null ? BigDecimal.ZERO : new BigDecimal(hours), typeId,
                typeId == null ? null : "إجازة", leavePaid, null);
    }

    @Test
    @DisplayName("a present day counts as worked and carries its hours")
    void present() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.PRESENT, "8", false),
                day(2, AttendanceStatus.PRESENT, "7.5", false)));

        assertEquals(money("2.00"), summary.workedDays());
        assertEquals(money("15.50"), summary.workedHours());
        assertEquals(money("0.00"), summary.absenceDays());
    }

    @Test
    @DisplayName("an absent day is deducted")
    void absent() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.ABSENT, null, false)));

        assertEquals(money("1.00"), summary.absenceDays());
        assertEquals(money("0.00"), summary.workedDays());
    }

    @Test
    @DisplayName("an approved paid leave is not absence - that is the whole point of approving it")
    void paidLeaveIsNotAbsence() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.LEAVE, null, true),
                day(2, AttendanceStatus.LEAVE, null, true)));

        assertEquals(money("0.00"), summary.absenceDays());
        assertEquals(money("0.00"), summary.workedDays(),
                "and it is not a worked day either - a daily wage is not paid for it");
    }

    @Test
    @DisplayName("an unpaid leave is absence, and the decision is the type's not the day's")
    void unpaidLeaveIsAbsence() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.LEAVE, null, false)));

        assertEquals(money("1.00"), summary.absenceDays());
    }

    @Test
    @DisplayName("a weekend is neither worked nor missed - both collapses are wrong in money")
    void weekendIsNeither() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.WEEKEND, null, false),
                day(2, AttendanceStatus.HOLIDAY, null, false)));

        assertEquals(money("0.00"), summary.workedDays(),
                "counting it as present pays a wage for a day nobody worked");
        assertEquals(money("0.00"), summary.absenceDays(),
                "counting it as absent deducts a rest day the employee is entitled to");
    }

    @Test
    @DisplayName("a weekend carrying hours does not contribute them - only a worked day does")
    void hoursOnlyComeFromWorkedDays() {
        AttendanceSummary summary = AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.WEEKEND, "8", false),
                day(2, AttendanceStatus.ABSENT, "8", false),
                day(3, AttendanceStatus.PRESENT, "6", false)));

        assertEquals(money("6.00"), summary.workedHours());
    }

    @Test
    @DisplayName("a whole month adds up to what a person would reach with a pen")
    void aWholeMonth() {
        List<AttendanceDay> month = new ArrayList<>();
        for (int i = 1; i <= 22; i++) {
            month.add(day(i, AttendanceStatus.PRESENT, "8", false));
        }
        for (int i = 23; i <= 26; i++) {
            month.add(day(i, AttendanceStatus.WEEKEND, null, false));
        }
        month.add(day(27, AttendanceStatus.LEAVE, null, true));
        month.add(day(28, AttendanceStatus.LEAVE, null, false));
        month.add(day(29, AttendanceStatus.ABSENT, null, false));
        month.add(day(30, AttendanceStatus.HOLIDAY, null, false));

        AttendanceSummary summary = AttendanceSummary.of(month);

        assertEquals(money("22.00"), summary.workedDays());
        assertEquals(money("176.00"), summary.workedHours());
        assertEquals(money("2.00"), summary.absenceDays(),
                "the unpaid leave and the absence; the paid leave, the weekends and the "
                        + "holiday cost nothing");
    }

    @Test
    @DisplayName("nothing recorded is empty, and tells the payroll to leave the month alone")
    void nothingRecorded() {
        assertTrue(AttendanceSummary.of(List.<AttendanceDay>of()).isEmpty());
        assertTrue(AttendanceSummary.EMPTY.isEmpty());
        assertTrue(AttendanceSummary.of((java.util.Collection<AttendanceDay>) null).isEmpty());

        assertEquals(false, AttendanceSummary.of(List.of(
                day(1, AttendanceStatus.PRESENT, "1", false))).isEmpty());
    }

    @Test
    @DisplayName("a leave day always names a type, and no other day may")
    void theLeaveTypeRule() {
        for (AttendanceStatus status : AttendanceStatus.values()) {
            assertEquals(status == AttendanceStatus.LEAVE, status.needsLeaveType(),
                    "V60 checks the same thing in both directions: " + status);
        }
    }
}
