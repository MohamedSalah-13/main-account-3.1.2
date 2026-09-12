package com.hamza.account.features.employee.payroll;

import com.hamza.account.features.employee.SalaryKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic, against the months that actually catch it.
 * <p>
 * {@code docs/employees-plan.md} §5 names the list: a 28-day month, a 31-day month, a
 * fraction of a day, somebody hired mid-month and somebody who left in it. Each is here
 * because each produces a plausible wrong answer - a payroll wrong by a day's pay looks
 * exactly like one that is right.
 */
class PayrollCalculatorTest {

    private static final PayrollPeriod JANUARY = new PayrollPeriod(2026, 1);   // 31 days
    private static final PayrollPeriod FEBRUARY = new PayrollPeriod(2026, 2);  // 28 days
    private static final PayrollPeriod LEAP_FEB = new PayrollPeriod(2028, 2);  // 29 days
    private static final PayrollPeriod SEPTEMBER = new PayrollPeriod(2026, 9); // 30 days

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static Input input() {
        return new Input();
    }

    /**
     * A builder for the record, kept in the test rather than beside the model: production
     * code always has every figure to hand, and a builder there would be an invitation to
     * construct a half-filled input somewhere it matters.
     */
    private static final class Input {
        private SalaryKind kind = SalaryKind.MONTHLY;
        private BigDecimal rate = BigDecimal.ZERO;
        private LocalDate hiredOn;
        private LocalDate endedOn;
        private BigDecimal absenceDays = BigDecimal.ZERO;
        private BigDecimal workedDays = BigDecimal.ZERO;
        private BigDecimal workedHours = BigDecimal.ZERO;
        private BigDecimal allowances = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private BigDecimal deductions = BigDecimal.ZERO;
        private BigDecimal advances = BigDecimal.ZERO;

        Input kind(SalaryKind value) { kind = value; return this; }
        Input rate(String value) { rate = new BigDecimal(value); return this; }
        Input hiredOn(LocalDate value) { hiredOn = value; return this; }
        Input endedOn(LocalDate value) { endedOn = value; return this; }
        Input absenceDays(String value) { absenceDays = new BigDecimal(value); return this; }
        Input workedDays(String value) { workedDays = new BigDecimal(value); return this; }
        Input workedHours(String value) { workedHours = new BigDecimal(value); return this; }
        Input allowances(String value) { allowances = new BigDecimal(value); return this; }
        Input commission(String value) { commission = new BigDecimal(value); return this; }
        Input deductions(String value) { deductions = new BigDecimal(value); return this; }
        Input advances(String value) { advances = new BigDecimal(value); return this; }

        PayrollInput build() {
            return new PayrollInput(7, "سها", kind, rate, hiredOn, endedOn,
                    absenceDays, workedDays, workedHours, allowances, commission, deductions,
                    advances);
        }
    }

    @Nested
    @DisplayName("a monthly salary")
    class Monthly {

        @Test
        @DisplayName("a full month is the rate, whatever the month's length")
        void fullMonth() {
            for (PayrollPeriod period : new PayrollPeriod[]{JANUARY, FEBRUARY, LEAP_FEB, SEPTEMBER}) {
                PayrollCalculation line = PayrollCalculator.calculate(period,
                        PayrollInput.monthly(7, "سها", money("3000")));
                assertEquals(money("3000.00"), line.basic(), "in " + period);
                assertEquals(money("3000.00"), line.netPay(), "in " + period);
            }
        }

        @Test
        @DisplayName("a day of absence costs the month's own length, not a flat thirtieth")
        void absenceUsesTheMonthsLength() {
            // 3100 over 31 days is exactly 100; over 28 it is 110.71; over 29 it is 106.90.
            // A flat /30 would answer 103.33 in all three - wrong in every month but June.
            assertEquals(money("100.00"), absenceCost(JANUARY, "3100", "1"));
            assertEquals(money("110.71"), absenceCost(FEBRUARY, "3100", "1"));
            assertEquals(money("106.90"), absenceCost(LEAP_FEB, "3100", "1"));
            assertEquals(money("103.33"), absenceCost(SEPTEMBER, "3100", "1"));
        }

        @Test
        @DisplayName("half a day is half a day")
        void fractionOfADay() {
            assertEquals(money("50.00"), absenceCost(JANUARY, "3100", "0.5"));
            assertEquals(money("25.00"), absenceCost(JANUARY, "3100", "0.25"));
        }

        @Test
        @DisplayName("absence can never cost more than the basic it comes out of")
        void absenceIsCappedAtTheBasic() {
            PayrollCalculation line = PayrollCalculator.calculate(FEBRUARY,
                    input().rate("3000").absenceDays("40").build());

            assertEquals(money("3000.00"), line.absenceDeduction(),
                    "a month recorded with more absence than it has days must not produce a "
                            + "negative basic dressed up as a deduction");
            assertEquals(money("0.00"), line.netPay());
        }

        @Test
        @DisplayName("somebody hired mid-month is paid for the part of it they worked")
        void hiredMidMonth() {
            // Hired on the 20th of a 30-day month: the 20th to the 30th is 11 days.
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").hiredOn(LocalDate.of(2026, 9, 20)).build());

            assertEquals(money("1100.00"), line.basic(), "3000 * 11 / 30");
        }

        @Test
        @DisplayName("somebody who left mid-month is paid to the day they left")
        void leftMidMonth() {
            // The 1st to the 10th is 10 days of 30.
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").endedOn(LocalDate.of(2026, 9, 10)).build());

            assertEquals(money("1000.00"), line.basic(), "3000 * 10 / 30");
        }

        @Test
        @DisplayName("hired before the month and still employed is a full month")
        void hiredLongAgo() {
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").hiredOn(LocalDate.of(2020, 1, 1)).build());

            assertEquals(money("3000.00"), line.basic());
        }

        @Test
        @DisplayName("somebody who left before the month earns nothing in it")
        void leftBeforeThePeriod() {
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").endedOn(LocalDate.of(2026, 8, 31)).build());

            assertEquals(money("0.00"), line.basic());
            assertTrue(line.isEmpty(), "a row of zeroes is not an entitlement");
        }

        private BigDecimal absenceCost(PayrollPeriod period, String rate, String days) {
            return PayrollCalculator.calculate(period,
                    input().rate(rate).absenceDays(days).build()).absenceDeduction();
        }
    }

    @Nested
    @DisplayName("the other three salary kinds")
    class OtherKinds {

        @Test
        @DisplayName("a daily wage is the rate times the days present")
        void daily() {
            PayrollCalculation line = PayrollCalculator.calculate(FEBRUARY,
                    input().kind(SalaryKind.DAILY).rate("150").workedDays("22").build());

            assertEquals(money("3300.00"), line.basic());
        }

        @Test
        @DisplayName("an hourly wage is the rate times the hours present, fractions included")
        void hourly() {
            PayrollCalculation line = PayrollCalculator.calculate(FEBRUARY,
                    input().kind(SalaryKind.HOURLY).rate("22.50").workedHours("173.5").build());

            assertEquals(money("3903.75"), line.basic());
        }

        @Test
        @DisplayName("a commission employee has no basic at all")
        void commissionHasNoBasic() {
            PayrollCalculation line = PayrollCalculator.calculate(FEBRUARY,
                    input().kind(SalaryKind.COMMISSION).rate("9999").commission("1200").build());

            assertEquals(money("0.00"), line.basic(), "the rate is not a basic for this kind");
            assertEquals(money("1200.00"), line.earned());
            assertEquals(money("1200.00"), line.netPay());
        }

        @Test
        @DisplayName("absence does not reduce a daily or hourly wage - that would take it twice")
        void absenceDoesNotTouchACountedWage() {
            // The days present are already the count. Deducting absence as well would charge
            // the same absence twice: once by not counting the day, once by taking it off.
            assertEquals(money("0.00"), PayrollCalculator.calculate(FEBRUARY,
                    input().kind(SalaryKind.DAILY).rate("150").workedDays("20").absenceDays("2")
                            .build()).absenceDeduction());

            assertEquals(money("0.00"), PayrollCalculator.calculate(FEBRUARY,
                    input().kind(SalaryKind.HOURLY).rate("20").workedHours("100").absenceDays("2")
                            .build()).absenceDeduction());
        }
    }

    @Nested
    @DisplayName("what the line adds up to")
    class Totals {

        @Test
        @DisplayName("earned is basic plus allowances plus commission, and net takes deductions off")
        void components() {
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").allowances("500").commission("250")
                            .deductions("150").absenceDays("1").build());

            assertEquals(money("3750.00"), line.earned(), "3000 + 500 + 250");
            assertEquals(money("100.00"), line.absenceDeduction(), "3000 / 30");
            assertEquals(money("250.00"), line.totalDeductions(), "100 absence + 150 by hand");
            assertEquals(money("3500.00"), line.netPay(), "3750 - 250");
        }

        @Test
        @DisplayName("net may be negative - a month whose deductions exceed its earnings is real")
        void netMayBeNegative() {
            PayrollCalculation line = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("1000").deductions("1500").build());

            assertEquals(money("-500.00"), line.netPay());
            assertEquals(money("1000.00"), line.earned(),
                    "and the entitlement is still the whole of what was earned");
        }
    }

    @Nested
    @DisplayName("an advance")
    class Advances {

        @Test
        @DisplayName("is never deducted here - it was deducted on the day the cash left")
        void advanceChangesNothing() {
            PayrollCalculation without = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").build());
            PayrollCalculation with = PayrollCalculator.calculate(SEPTEMBER,
                    input().rate("3000").advances("1000").build());

            assertEquals(without.netPay(), with.netPay(),
                    "rule ق-٥: deducting it again charges the employee twice for one payment");
            assertEquals(without.earned(), with.earned());
            assertEquals(without.totalDeductions(), with.totalDeductions());
        }
    }

    @Nested
    @DisplayName("rounding")
    class Rounding {

        @Test
        @DisplayName("is HALF_UP, so a total never exceeds the sum of its own rows")
        void halfUp() {
            // 1000 / 3 days of a 31-day month. CEILING - what the Jasper templates used -
            // would answer 96.78 here and make a printed total larger than its rows.
            PayrollCalculation line = PayrollCalculator.calculate(JANUARY,
                    input().rate("1000").absenceDays("3").build());

            assertEquals(money("96.77"), line.absenceDeduction());
        }

        @Test
        @DisplayName("every figure carries two places, even a zero")
        void everyFigureIsScaled() {
            PayrollCalculation line = PayrollCalculator.calculate(JANUARY,
                    input().kind(SalaryKind.COMMISSION).build());

            for (BigDecimal figure : new BigDecimal[]{line.basic(), line.allowances(),
                    line.commission(), line.absenceDeduction(), line.deductions(), line.netPay()}) {
                assertEquals(2, figure.scale(), "unscaled: " + figure);
            }
        }
    }

    @Nested
    @DisplayName("the period itself")
    class Period {

        @Test
        @DisplayName("knows its own length, including a leap February")
        void lengths() {
            assertEquals(31, JANUARY.lengthInDays());
            assertEquals(28, FEBRUARY.lengthInDays());
            assertEquals(29, LEAP_FEB.lengthInDays());
            assertEquals(30, SEPTEMBER.lengthInDays());
        }

        @Test
        @DisplayName("counts the days somebody was on the books, both ends inclusive")
        void employedDays() {
            assertEquals(30, SEPTEMBER.employedDays(null, null));
            assertEquals(11, SEPTEMBER.employedDays(LocalDate.of(2026, 9, 20), null));
            assertEquals(10, SEPTEMBER.employedDays(null, LocalDate.of(2026, 9, 10)));
            assertEquals(6, SEPTEMBER.employedDays(LocalDate.of(2026, 9, 5),
                    LocalDate.of(2026, 9, 10)));
            assertEquals(0, SEPTEMBER.employedDays(LocalDate.of(2026, 10, 1), null));
            assertEquals(0, SEPTEMBER.employedDays(null, LocalDate.of(2026, 8, 1)));
        }

        @Test
        @DisplayName("the entitlement is dated the last day - a month is earned once it ends")
        void lastDay() {
            assertEquals(LocalDate.of(2026, 2, 28), FEBRUARY.lastDay());
            assertEquals(LocalDate.of(2028, 2, 29), LEAP_FEB.lastDay());
            assertEquals(LocalDate.of(2026, 1, 31), JANUARY.lastDay());
        }
    }
}
