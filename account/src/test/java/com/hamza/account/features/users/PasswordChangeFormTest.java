package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeFormTest {

    @Test
    void requiredFieldsAreReportedInEntryOrder() {
        assertEquals("password.change.error.current.required",
                new PasswordChangeForm("", "", "").firstErrorKey().orElseThrow());
        assertEquals("password.change.error.new.required",
                new PasswordChangeForm("current-pass", "", "").firstErrorKey().orElseThrow());
        assertEquals("password.change.error.confirmation.required",
                new PasswordChangeForm("current-pass", "new-pass", "").firstErrorKey().orElseThrow());
    }

    @Test
    void theDocumentedEightCharacterMinimumIsTheOnlySavePolicy() {
        assertEquals("user.password.minimum",
                new PasswordChangeForm("current-pass", "1234567", "1234567").firstErrorKey().orElseThrow());
        assertTrue(new PasswordChangeForm("current-pass", "12345678", "12345678").isReady());
        assertTrue(new PasswordChangeForm("current-pass", " leading and trailing ",
                " leading and trailing ").isReady());
    }

    @Test
    void confirmationMustMatchExactly() {
        PasswordChangeForm form = new PasswordChangeForm("current-pass", "New-pass1", "new-pass1");

        assertEquals("password.mismatch", form.firstErrorKey().orElseThrow());
        assertFalse(form.confirmationMatches());
    }

    @Test
    void theStrengthMeterIsInformationalAndRewardsLengthAndVariety() {
        assertEquals(PasswordStrength.EMPTY, new PasswordChangeForm("x", "", "").strength());
        assertEquals(PasswordStrength.WEAK, new PasswordChangeForm("x", "short", "").strength());
        assertEquals(PasswordStrength.FAIR, new PasswordChangeForm("x", "abcdefgh", "").strength());
        assertEquals(PasswordStrength.STRONG,
                new PasswordChangeForm("x", "Longer-Pass12", "").strength());
    }
}
