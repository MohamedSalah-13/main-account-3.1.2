package com.hamza.account.features.treasury.statement;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The number on the treasury statement's filters button. */
class TreasuryStatementFilterPanelCountTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 16);

    @Test
    void theTreasuryAndThePeriodAreNotCounted() {
        assertEquals(0, new TreasuryStatementFilter(FROM, TO, 3, null, null, 0, 50).panelConditionCount());
    }

    @Test
    void theMovementKindAndTheUserAreEachOne() {
        assertEquals(2, new TreasuryStatementFilter(FROM, TO, null, TreasuryMovementKind.values()[0], 4, 0, 50)
                .panelConditionCount());
    }
}
