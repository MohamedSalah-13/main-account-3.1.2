package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The number on the statement's filters button: what its closed panel is still narrowing. */
class PartyStatementFilterPanelCountTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 16);

    @Test
    void aPeriodAndTextAloneCountNothing() {
        assertEquals(0, new PartyStatementFilter(PartyKind.CUSTOMER, 7, FROM, TO, Set.of(), null, null,
                null, null, "INV-12", false, 0, 100).panelConditionCount());
    }

    @Test
    void eachNarrowingConditionCountsOnce() {
        assertEquals(6, new PartyStatementFilter(PartyKind.CUSTOMER, 7, FROM, TO,
                Set.of(PartyMovementKind.values()[0]), 2, 3, BigDecimal.ZERO, BigDecimal.TEN, "",
                true, 0, 100).panelConditionCount());
    }
}
