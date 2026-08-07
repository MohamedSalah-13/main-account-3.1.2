package com.hamza.controlsfx.others;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boundary of the "no invoice dated after today" rule. Today itself must
 * stay allowed - the whole point is that invoices are written on the day they
 * happen - so the check is strictly {@code after}, not {@code not before}.
 */
class DateSettingTest {

    @Test
    @DisplayName("today is not in the future")
    void todayIsAllowed() {
        assertFalse(DateSetting.isInTheFuture(LocalDate.now()));
    }

    @Test
    @DisplayName("tomorrow is in the future")
    void tomorrowIsRejected() {
        assertTrue(DateSetting.isInTheFuture(LocalDate.now().plusDays(1)));
    }

    @Test
    @DisplayName("past dates stay allowed, so a late entry can still be back-dated")
    void pastIsAllowed() {
        assertFalse(DateSetting.isInTheFuture(LocalDate.now().minusDays(1)));
        assertFalse(DateSetting.isInTheFuture(LocalDate.now().minusYears(2)));
    }

    @Test
    @DisplayName("an empty picker is not treated as a future date")
    void nullIsNotFuture() {
        // The caller reports a missing date separately; conflating the two would
        // show "date is in the future" for a picker the user simply cleared.
        assertFalse(DateSetting.isInTheFuture(null));
    }
}
