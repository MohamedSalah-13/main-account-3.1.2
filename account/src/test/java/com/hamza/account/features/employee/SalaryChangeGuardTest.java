package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the salary box may still write, and when it has become a rewrite of history. */
class SalaryChangeGuardTest {

    @Test
    @DisplayName("one dated rate is still a correction; two are a history")
    void theBoundary() {
        assertTrue(SalaryChangeGuard.mayCorrectHireRate(0), "an employee with no rate yet");
        assertTrue(SalaryChangeGuard.mayCorrectHireRate(1), "the row written when they were hired");
        assertFalse(SalaryChangeGuard.mayCorrectHireRate(2),
                "rewriting the first of two changes what a past month was calculated from");
    }

    @Test
    @DisplayName("the refusal carries the key that names the road, not a sentence")
    void refusalKey() {
        assertEquals("employee.error.salary.locked",
                assertThrows(UserValidationException.class,
                        () -> SalaryChangeGuard.requireCorrectable(3)).getMessage());
    }

    @Test
    @DisplayName("100 and 100.00 are the same salary")
    void scaleIsNotAChange() {
        assertFalse(SalaryChangeGuard.isChanged(SalaryKind.MONTHLY, new BigDecimal("100"),
                        SalaryKind.MONTHLY, new BigDecimal("100.00")),
                "a form that round-trips through a text field produces one or the other, and an "
                        + "edit that changes nothing must not be refused as though it did");
    }

    @Test
    @DisplayName("changing how somebody is paid is a change, even at the same figure")
    void kindCounts() {
        assertTrue(SalaryChangeGuard.isChanged(SalaryKind.MONTHLY, new BigDecimal("100"),
                SalaryKind.DAILY, new BigDecimal("100")));
    }

    @Test
    @DisplayName("an employee with no rate at all is changed by being given one")
    void fromNothing() {
        assertTrue(SalaryChangeGuard.isChanged(SalaryKind.MONTHLY, null,
                SalaryKind.MONTHLY, BigDecimal.TEN));
        assertFalse(SalaryChangeGuard.isChanged(SalaryKind.MONTHLY, null, SalaryKind.MONTHLY, null));
    }

    @Test
    @DisplayName("a real change is a change")
    void plainChange() {
        assertTrue(SalaryChangeGuard.isChanged(SalaryKind.MONTHLY, new BigDecimal("3000"),
                SalaryKind.MONTHLY, new BigDecimal("3500")));
    }
}
