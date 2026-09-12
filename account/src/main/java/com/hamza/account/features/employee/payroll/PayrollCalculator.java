package com.hamza.account.features.employee.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What one employee is owed for one month. No database, no JavaFX, no clock.
 * <p>
 * It is a class of its own because its mistakes are arithmetic, and arithmetic is the one
 * kind of mistake that is cheap to test and expensive to find in production: a payroll that
 * is wrong by a day's pay looks exactly like a payroll that is right.
 *
 * <h2>Money is BigDecimal and rounds HALF_UP</h2>
 * Every figure is a {@link BigDecimal} scaled to two places with {@link RoundingMode#HALF_UP},
 * because {@code double} cannot hold 0.1 and because {@code ROUND_CEILING} - which the Jasper
 * templates used - produced a printed total larger than the sum of the rows above it
 * (see {@code CLAUDE.md}, "Printed reports").
 *
 * <h2>An advance is never deducted here</h2>
 * {@link PayrollInput#advancesOutstanding()} is read by nothing in this class. It is carried
 * so a payslip can print it. The advance left the till on its own day, was an expense row
 * then, and has been a debit on the employee since (rule ق-٥ in
 * {@code docs/employees-plan.md}). Subtracting it again here would charge the employee twice
 * for one payment - and the balance would be wrong by exactly the advance, in the direction
 * that favours the business.
 */
public final class PayrollCalculator {

    /** Money: two places. */
    private static final int MONEY_SCALE = 2;

    /** Intermediate division: enough places that a daily rate does not lose a piastre. */
    private static final int WORKING_SCALE = 6;

    private PayrollCalculator() {
    }

    /**
     * Calculate one line.
     *
     * @param period what month, which is where the number of days comes from
     * @param input  everything about the employee in that month
     */
    public static PayrollCalculation calculate(PayrollPeriod period, PayrollInput input) {
        if (period == null) {
            throw new IllegalArgumentException("A period is required");
        }
        if (input == null) {
            throw new IllegalArgumentException("An input is required");
        }

        BigDecimal basic = basicFor(period, input);
        BigDecimal absenceDeduction = absenceDeductionFor(period, input);

        // An absence can never cost more than the basic it is taken out of. Without this a
        // month recorded with more absence days than the employee was employed would produce
        // a negative basic dressed up as a deduction.
        if (absenceDeduction.compareTo(basic) > 0) {
            absenceDeduction = basic;
        }

        BigDecimal allowances = money(input.allowances());
        BigDecimal commission = money(input.commission());
        BigDecimal deductions = money(input.manualDeductions());

        BigDecimal earned = basic.add(allowances).add(commission);
        BigDecimal netPay = earned.subtract(absenceDeduction).subtract(deductions);

        return new PayrollCalculation(input.employeeId(), input.salaryKind(),
                money(input.rate()), basic, allowances, commission, absenceDeduction,
                deductions, money(netPay));
    }

    /**
     * What the salary kind produces before anything is added or taken off.
     * <p>
     * A {@code MONTHLY} rate is prorated by the days the employee was actually on the books,
     * so somebody hired on the 20th is paid for the part of the month they worked rather than
     * for all of it. A {@code DAILY} or {@code HOURLY} rate is not prorated - it already is a
     * count of what happened. A {@code COMMISSION} employee has no basic at all.
     */
    private static BigDecimal basicFor(PayrollPeriod period, PayrollInput input) {
        return switch (input.salaryKind()) {
            case MONTHLY -> {
                BigDecimal rate = money(input.rate());
                int employed = period.employedDays(input.hiredOn(), input.endedOn());
                int inMonth = period.lengthInDays();
                if (employed >= inMonth) {
                    yield rate;
                }
                yield money(rate.multiply(BigDecimal.valueOf(employed))
                        .divide(BigDecimal.valueOf(inMonth), WORKING_SCALE, RoundingMode.HALF_UP));
            }
            case DAILY -> money(input.rate().multiply(input.workedDays()));
            case HOURLY -> money(input.rate().multiply(input.workedHours()));
            case COMMISSION -> BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        };
    }

    /**
     * What unpaid absence costs.
     * <p>
     * Only a monthly salary is reduced by it: a daily or hourly wage is already a count of
     * the days or hours that happened, so deducting absence from it would take the same
     * absence off twice. A commission employee has no basic to reduce.
     * <p>
     * The day's worth is the month's rate over the month's own length - 28, 29, 30 or 31 -
     * never a constant 30, which would overpay a day in February and underpay one in January.
     */
    private static BigDecimal absenceDeductionFor(PayrollPeriod period, PayrollInput input) {
        if (input.salaryKind() != com.hamza.account.features.employee.SalaryKind.MONTHLY) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (input.absenceDays().signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal dayRate = money(input.rate())
                .divide(BigDecimal.valueOf(period.lengthInDays()), WORKING_SCALE,
                        RoundingMode.HALF_UP);
        return money(dayRate.multiply(input.absenceDays()));
    }

    /** Two places, HALF_UP, and never null. */
    public static BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
