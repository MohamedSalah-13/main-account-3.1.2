package com.hamza.account.trial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reminder to activate while the trial lasts: once it is over the program closes at start-up, before
 * the About window - where the code is typed - can be opened.
 */
class OnlineLicensingTest {

    private static TrialManager.TrialDisplayInfo trial(Long daysRemaining) {
        TrialManager.TrialDisplayInfo info = new TrialManager.TrialDisplayInfo();
        info.daysRemaining = daysRemaining;
        info.trialExpired = daysRemaining != null && daysRemaining <= 0;
        return info;
    }

    @Test
    void theLastDaysOfATrialAreReminded() {
        for (long days = 1; days <= OnlineLicensing.TRIAL_REMINDER_DAYS; days++) {
            assertTrue(OnlineLicensing.trialEnding(trial(days)), days + " days left");
        }
    }

    @Test
    void nothingElseIs() {
        assertFalse(OnlineLicensing.trialEnding(null));
        assertFalse(OnlineLicensing.trialEnding(trial(OnlineLicensing.TRIAL_REMINDER_DAYS + 1L)), "early in the trial");
        assertFalse(OnlineLicensing.trialEnding(trial(0L)), "over - the reminder could not be acted on");
        assertFalse(OnlineLicensing.trialEnding(trial(null)), "not read");

        TrialManager.TrialDisplayInfo licensed = trial(2L);
        licensed.licenseValid = true;
        assertFalse(OnlineLicensing.trialEnding(licensed), "an older licence already licenses it");

        TrialManager.TrialDisplayInfo unread = trial(2L);
        unread.error = "no database";
        assertFalse(OnlineLicensing.trialEnding(unread));
    }
}
