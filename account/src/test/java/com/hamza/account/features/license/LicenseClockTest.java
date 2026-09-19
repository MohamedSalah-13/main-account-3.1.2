package com.hamza.account.features.license;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseClockTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    @Test
    void theComputerAloneIsBelieved() {
        LicenseClock clock = LicenseClock.of(TODAY, null, null);
        assertEquals(TODAY, clock.today());
        assertFalse(clock.rolledBack());
    }

    /** Setting the computer back gains nothing: the later source is the day. */
    @Test
    void aComputerSetBackIsOverruledByTheDatabase() {
        LicenseClock clock = LicenseClock.of(LocalDate.of(2009, 1, 1), TODAY, null);
        assertEquals(TODAY, clock.today());
        assertTrue(clock.rolledBack());
    }

    @Test
    void aComputerSetBackIsOverruledByTheLastDaySeen() {
        LicenseClock clock = LicenseClock.of(TODAY.minusDays(40), TODAY.minusDays(40), TODAY);
        assertEquals(TODAY, clock.today());
        assertTrue(clock.rolledBack());
    }

    /** A database server running behind is not the computer's clock being moved. */
    @Test
    void aComputerAheadOfTheOthersIsNotRolledBack() {
        LicenseClock clock = LicenseClock.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(3));
        assertEquals(TODAY, clock.today());
        assertFalse(clock.rolledBack());
    }
}
