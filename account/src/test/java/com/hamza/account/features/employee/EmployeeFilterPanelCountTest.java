package com.hamza.account.features.employee;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The number on the employees screen's filters button, which has to say the list is narrowed while the panel is closed. */
class EmployeeFilterPanelCountTest {

    @Test
    void theWholeListCountsNothing() {
        assertEquals(0, EmployeeFilter.all().panelConditionCount());
    }

    @Test
    void theTextTypedInTheBarIsNotCounted() {
        assertEquals(0, EmployeeFilter.all().withText("ahmed").panelConditionCount());
    }

    @Test
    void eachBoundCountsOnItsOwnAndZeroIsABound() {
        EmployeeFilter narrowed = new EmployeeFilter("", 2, EmployeeState.ACTIVE, null, null,
                LocalDate.of(2026, 1, 1), null, BigDecimal.ZERO, null, true, 0, 50);
        assertEquals(5, narrowed.panelConditionCount());
    }
}
