package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** What the two entry forms refuse, and the message key each refusal carries. */
class EmployeeAccountEntryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 2, 20);

    private static String keyOf(Runnable work) {
        return assertThrows(UserValidationException.class, () -> {
            try {
                work.run();
            } catch (RuntimeException wrapper) {
                if (wrapper.getCause() instanceof UserValidationException refusal) {
                    throw refusal;
                }
                throw wrapper;
            }
        }).getMessage();
    }

    private static Runnable ledger(int employeeId, LocalDate date, EmployeeEntryKind kind,
                                   BigDecimal amount, String notes) {
        return () -> {
            try {
                EmployeeLedgerEntry.parse(employeeId, date, kind, amount, notes);
            } catch (UserValidationException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Runnable payment(BigDecimal amount, int treasuryId, int heading) {
        return () -> {
            try {
                EmployeePayment.parse(7, DAY, amount, EmployeeCashPurpose.SALARY, treasuryId,
                        heading, null);
            } catch (UserValidationException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Nested
    @DisplayName("a movement with no cash behind it")
    class Ledger {

        @Test
        @DisplayName("needs an employee, a date, a kind and a figure above zero")
        void required() {
            assertEquals("employee.error.account.employee",
                    keyOf(ledger(0, DAY, EmployeeEntryKind.DEDUCTION, BigDecimal.TEN, null)));
            assertEquals("employee.error.account.date",
                    keyOf(ledger(7, null, EmployeeEntryKind.DEDUCTION, BigDecimal.TEN, null)));
            assertEquals("employee.error.account.kind",
                    keyOf(ledger(7, DAY, null, BigDecimal.TEN, null)));
            assertEquals("employee.error.account.amount",
                    keyOf(ledger(7, DAY, EmployeeEntryKind.DEDUCTION, BigDecimal.ZERO, null)));
        }

        @Test
        @DisplayName("refuses a negative, because the direction is the kind's and not the sign's")
        void directionIsNotTheSign() {
            assertEquals("employee.error.account.amount",
                    keyOf(ledger(7, DAY, EmployeeEntryKind.DEDUCTION, new BigDecimal("-5"), null)));
        }

        @Test
        @DisplayName("refuses a kind a run produces, which nothing has approved by hand")
        void automaticKind() {
            assertEquals("employee.error.account.kind.automatic",
                    keyOf(ledger(7, DAY, EmployeeEntryKind.COMMISSION, BigDecimal.TEN, null)));
        }

        @Test
        @DisplayName("lets an entitlement be recorded, or phase B shows pay out and nothing earned")
        void entitlementIsAllowed() throws Exception {
            EmployeeLedgerEntry entry = EmployeeLedgerEntry.parse(7, DAY,
                    EmployeeEntryKind.ENTITLEMENT, new BigDecimal("5000"), "  فبراير  ");
            assertEquals(EmployeeEntryKind.ENTITLEMENT, entry.kind());
            assertEquals(new BigDecimal("5000.00"), entry.amount());
            assertEquals("فبراير", entry.notes());
        }

        @Test
        @DisplayName("stores a blank note as nothing rather than as an empty string")
        void blankNote() throws Exception {
            assertNull(EmployeeLedgerEntry.parse(7, DAY, EmployeeEntryKind.BONUS,
                    BigDecimal.TEN, "   ").notes());
        }

        @Test
        @DisplayName("refuses more than the column holds")
        void range() {
            assertEquals("employee.error.account.amount.range",
                    keyOf(ledger(7, DAY, EmployeeEntryKind.BONUS,
                            new BigDecimal("1000000000000"), null)));
            assertEquals("employee.error.account.notes.length",
                    keyOf(ledger(7, DAY, EmployeeEntryKind.BONUS, BigDecimal.TEN,
                            "ا".repeat(256))));
        }
    }

    @Nested
    @DisplayName("cash leaving a till")
    class Payment {

        @Test
        @DisplayName("needs a figure, a till and a heading")
        void required() {
            assertEquals("employee.error.pay.amount", keyOf(payment(BigDecimal.ZERO, 1, 1)));
            assertEquals("employee.error.pay.treasury", keyOf(payment(BigDecimal.TEN, 0, 1)));
            assertEquals("employee.error.pay.heading", keyOf(payment(BigDecimal.TEN, 1, 0)));
        }

        @Test
        @DisplayName("is a salary when nothing says otherwise - what the existing rows already mean")
        void defaultsToSalary() throws Exception {
            EmployeePayment paid = EmployeePayment.parse(7, DAY, new BigDecimal("4000.005"), null,
                    1, 1, null);
            assertEquals(EmployeeCashPurpose.SALARY, paid.purpose());
            assertEquals(new BigDecimal("4000.01"), paid.amount(),
                    "money rounds HALF_UP, here as everywhere");
        }
    }

    @Nested
    @DisplayName("the purpose a payment with no row of its own carries")
    class Purposes {

        @Test
        @DisplayName("is a salary, which is what years of emp_id rows already mean")
        void orSalary() {
            assertEquals(EmployeeCashPurpose.SALARY, EmployeeCashPurpose.orSalary(null));
            assertEquals(EmployeeCashPurpose.SALARY, EmployeeCashPurpose.orSalary("  "));
            assertEquals(EmployeeCashPurpose.ADVANCE, EmployeeCashPurpose.orSalary("ADVANCE"));
        }

        @Test
        @DisplayName("names the value when it cannot be read, rather than failing anonymously")
        void unknown() {
            assertEquals("Unknown employee cash purpose: LOAN",
                    assertThrows(IllegalArgumentException.class,
                            () -> EmployeeCashPurpose.of("LOAN")).getMessage());
        }
    }
}
