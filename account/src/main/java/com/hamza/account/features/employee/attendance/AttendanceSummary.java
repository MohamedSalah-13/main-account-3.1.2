package com.hamza.account.features.employee.attendance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * What a month of days comes to for one employee: the three figures the payroll needs.
 * <p>
 * It is computed in Java from the days rather than summed in SQL, and that is deliberate. The
 * absence rule is not a column - it is "absent, or on a leave whose <i>type</i> is unpaid" -
 * and writing it once here, where it can be tested against a fixture, is what keeps the
 * payroll's SELECT and this class from drifting into two answers. The repository reads days;
 * this decides what they mean.
 *
 * @param workedDays  days present - what a daily wage multiplies
 * @param absenceDays days deducted from a monthly salary
 * @param workedHours hours present - what an hourly wage multiplies
 */
public record AttendanceSummary(BigDecimal workedDays, BigDecimal absenceDays,
                                BigDecimal workedHours) {

    public static final AttendanceSummary EMPTY = new AttendanceSummary(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    /**
     * Folds a month of days.
     * <p>
     * A weekend and a holiday are counted as neither: the monthly salary is not reduced by a
     * rest day, and a daily wage is not paid for one. Both collapses - counting them as
     * present, or as absent - are wrong in money and in opposite directions.
     */
    public static AttendanceSummary of(Collection<AttendanceDay> days) {
        if (days == null || days.isEmpty()) {
            return EMPTY;
        }
        BigDecimal worked = BigDecimal.ZERO;
        BigDecimal absent = BigDecimal.ZERO;
        BigDecimal hours = BigDecimal.ZERO;
        for (AttendanceDay day : days) {
            if (day.status().isWorked()) {
                worked = worked.add(BigDecimal.ONE);
                hours = hours.add(day.hours() == null ? BigDecimal.ZERO : day.hours());
            } else if (day.countsAsAbsence()) {
                absent = absent.add(BigDecimal.ONE);
            }
        }
        return new AttendanceSummary(scale(worked), scale(absent), scale(hours));
    }

    /** A month with nothing recorded at all - so the payroll can tell it from a month of zeroes. */
    public boolean isEmpty() {
        return workedDays.signum() == 0 && absenceDays.signum() == 0
                && workedHours.signum() == 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Convenience for tests and callers holding a list. */
    public static AttendanceSummary of(List<AttendanceDay> days) {
        return of((Collection<AttendanceDay>) days);
    }
}
