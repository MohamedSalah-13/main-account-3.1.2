package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bound of zero is a filter; no bound is not.
 * <p>
 * <b>This is a defect the screen shipped with, and only opening it found.</b>
 * {@code Utils.setTextFormatter} seeds a field with {@code 0.0} - right for an amount being
 * entered, since a payment starts at zero - so both "empty" boxes on the balances screen read
 * {@code 0.0} and the filter that reached SQL was {@code balance >= 0 AND balance <= 0}. The screen
 * opened showing only the parties whose account came to nothing: 118 of 145, with a total owed of
 * zero on a database owing 13,225, and nobody had typed anything. Every unit test passed, because
 * the filter record was doing exactly what it was told.
 * <p>
 * The screens use {@code Utils.setOptionalNumberFormatter} now, which leaves a filter box empty. The
 * distinction it turns on is the one asserted here: the record must be able to say "no bound", and
 * "zero" must remain a real bound a user can ask for.
 */
class PartyBalanceFilterZeroTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 10);

    private static PartyBalanceFilter with(BigDecimal min, BigDecimal max) {
        return new PartyBalanceFilter(PartyKind.CUSTOMER, AS_OF, null, BalanceState.ALL,
                min, max, null, null, false, null, "", 0, 50);
    }

    @Test
    @DisplayName("no bound adds no condition, so an untouched screen shows everybody")
    void noBoundAddsNoCondition() {
        String sql = PartyBalanceQuery.pageSql(with(null, null));
        assertFalse(sql.contains("HAVING"),
                "an untouched filter must not narrow anything: " + sql);
    }

    /**
     * The exact shape the screen produced: both boxes at zero, which is a real filter and would be
     * right if a user had asked for it.
     */
    @Test
    @DisplayName("a bound of zero is still a bound - the record cannot guess it was not meant")
    void zeroIsARealBound() {
        String sql = PartyBalanceQuery.pageSql(with(BigDecimal.ZERO, BigDecimal.ZERO));
        assertTrue(sql.contains("HAVING balance >= ? AND balance <= ?"),
                "zero must remain askable: " + sql);
    }

    @Test
    void oneBoundOnItsOwnIsHonoured() {
        assertTrue(PartyBalanceQuery.pageSql(with(BigDecimal.TEN, null))
                .contains("HAVING balance >= ?"));
        assertTrue(PartyBalanceQuery.pageSql(with(null, BigDecimal.TEN))
                .contains("HAVING balance <= ?"));
    }
}
