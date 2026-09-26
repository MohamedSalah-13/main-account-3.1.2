package com.hamza.account.features.about;

import com.hamza.account.features.about.AboutLicense.Tone;
import com.hamza.account.features.license.LicenseDecision;
import com.hamza.account.features.license.LicenseStatus;
import com.hamza.account.features.license.LicenseTerms;
import com.hamza.account.trial.TrialManager.TrialDisplayInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AboutLicenseTest {

    @Test
    @DisplayName("nothing read, or the check failed: every line says unavailable, in the warning's worst tone")
    void unavailable() {
        assertEquals(AboutLicense.UNAVAILABLE, AboutLicense.of(null));
        TrialDisplayInfo failed = new TrialDisplayInfo();
        failed.error = "reference 42";
        assertEquals(AboutLicense.UNAVAILABLE, AboutLicense.of(failed));
    }

    @Test
    @DisplayName("a valid licence: activated, unlimited, valid")
    void licensed() {
        TrialDisplayInfo info = new TrialDisplayInfo();
        info.licensePresent = true;
        info.licenseValid = true;
        AboutLicense license = AboutLicense.of(info);
        assertEquals(Tone.GOOD, license.tone());
        assertEquals("about.status.activated", license.statusKey());
        assertEquals("about.remaining.unlimited", license.remainingKey());
        assertEquals("about.license.valid", license.fileKey());
        assertNull(license.remainingDays());
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private static TrialDisplayInfo licensedInfo() {
        TrialDisplayInfo info = new TrialDisplayInfo();
        info.licensePresent = true;
        info.licenseValid = true;
        return info;
    }

    private static LicenseDecision server(LicenseStatus status, LocalDate updatesUntil, LocalDate expires) {
        return new LicenseDecision(status, Optional.of(new LicenseTerms("MACHINE", "C00001", "full",
                LocalDate.of(2026, 9, 26), updatesUntil, expires)), TODAY);
    }

    @Test
    @DisplayName("a perpetual licence from the server: perpetual, and the last day of its updates")
    void perpetualServerLicence() {
        AboutLicense license = AboutLicense.of(licensedInfo(),
                server(LicenseStatus.ACTIVE, LocalDate.of(2026, 12, 31), null));
        assertEquals(Tone.GOOD, license.tone());
        assertEquals("about.status.activated", license.statusKey());
        assertEquals("about.remaining.perpetual", license.remainingKey());
        assertNull(license.remainingDays());
        assertNull(license.remainingDate());
        assertEquals("about.updates.until", license.updatesKey());
        assertEquals(LocalDate.of(2026, 12, 31), license.updatesUntil());
    }

    @Test
    @DisplayName("a subscription: the day it runs to and the days left, that day counted")
    void subscription() {
        AboutLicense license = AboutLicense.of(licensedInfo(),
                server(LicenseStatus.ACTIVE, LocalDate.of(2027, 9, 26), LocalDate.of(2026, 10, 5)));
        assertEquals("about.remaining.subscription", license.remainingKey());
        assertEquals(LocalDate.of(2026, 10, 5), license.remainingDate());
        assertEquals(10L, license.remainingDays());
    }

    @Test
    @DisplayName("a subscription over: its grace days, then ended on its day - never the trial's words")
    void subscriptionOver() {
        AboutLicense grace = AboutLicense.of(licensedInfo(),
                server(LicenseStatus.GRACE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)));
        assertEquals(Tone.WARNING, grace.tone());
        assertEquals("about.status.grace", grace.statusKey());
        assertEquals("about.remaining.grace", grace.remainingKey());
        assertEquals(9L, grace.remainingDays(), "the grace runs fourteen days past the 20th, to 4 October, that day counted");

        AboutLicense ended = AboutLicense.of(licensedInfo(),
                server(LicenseStatus.READ_ONLY, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1)));
        assertEquals(Tone.BAD, ended.tone());
        assertEquals("about.status.read.only", ended.statusKey());
        assertEquals("about.remaining.read.only", ended.remainingKey());
        assertEquals(LocalDate.of(2026, 8, 1), ended.remainingDate());
        assertEquals("about.license.valid", ended.fileKey());
    }

    @Test
    @DisplayName("updates past their last day are said to have ended; the licence itself still stands")
    void updatesEnded() {
        AboutLicense license = AboutLicense.of(licensedInfo(),
                server(LicenseStatus.ACTIVE, LocalDate.of(2026, 9, 25), null));
        assertEquals(Tone.GOOD, license.tone());
        assertEquals("about.updates.ended", license.updatesKey());
    }

    @Test
    @DisplayName("the older licence has no dates: unlimited, and no updates line")
    void olderLicenceHasNoDates() {
        for (LicenseDecision server : new LicenseDecision[]{null,
                new LicenseDecision(LicenseStatus.ABSENT, Optional.empty(), TODAY)}) {
            AboutLicense license = AboutLicense.of(licensedInfo(), server);
            assertEquals("about.remaining.unlimited", license.remainingKey());
            assertNull(license.updatesKey());
        }
    }

    @Test
    @DisplayName("a trial running: its days, and whether a file is there that does not license this machine")
    void trial() {
        TrialDisplayInfo info = new TrialDisplayInfo();
        info.daysRemaining = 5L;
        AboutLicense license = AboutLicense.of(info);
        assertEquals(Tone.WARNING, license.tone());
        assertEquals("about.status.trial", license.statusKey());
        assertEquals(5L, license.remainingDays());
        assertEquals("about.license.missing", license.fileKey());

        info.licensePresent = true;
        assertEquals("about.license.invalid", AboutLicense.of(info).fileKey());
    }

    @Test
    @DisplayName("a trial over: zero days, never a negative count, and said as over")
    void expired() {
        TrialDisplayInfo info = new TrialDisplayInfo();
        info.daysRemaining = -12L;
        info.trialExpired = true;
        AboutLicense license = AboutLicense.of(info);
        assertEquals(Tone.BAD, license.tone());
        assertEquals("about.status.expired", license.statusKey());
        assertEquals(0L, license.remainingDays());
    }

    @Test
    @DisplayName("a trial whose start could not be read: no count to give")
    void noInstallationDate() {
        AboutLicense license = AboutLicense.of(new TrialDisplayInfo());
        assertEquals("about.remaining.unavailable", license.remainingKey());
        assertNull(license.remainingDays());
    }

    private static List<String> placeholders(String message) {
        List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%[sd]|\\{\\d}").matcher(message);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    /**
     * The screen resolves these through a variable, so {@code MessageKeyArchitectureTest} cannot see
     * them; they are checked against the three bundles here instead.
     */
    @Test
    @DisplayName("every key a licence line can carry is in the three bundles")
    void everyKeyIsTranslated() throws Exception {
        List<String> keys = List.of("about.status.unavailable", "about.status.activated", "about.status.trial",
                "about.status.expired", "about.remaining.unavailable", "about.remaining.unlimited",
                "about.remaining.days", "about.license.unavailable", "about.license.valid", "about.license.invalid",
                "about.license.missing", "about.remaining.perpetual", "about.remaining.subscription",
                "about.remaining.grace", "about.remaining.read.only", "about.status.grace", "about.status.read.only",
                "about.updates.until", "about.updates.ended");
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
            // The screen passes the date first and the days second, each only when it has one; a message
            // taking them in another order would fail at String.format, on the customer's screen.
            assertEquals(List.of("%s", "%d"), placeholders(properties.getProperty("about.remaining.subscription")), bundle);
            assertEquals(List.of("%d"), placeholders(properties.getProperty("about.remaining.grace")), bundle);
            assertEquals(List.of("%d"), placeholders(properties.getProperty("about.remaining.days")), bundle);
            assertEquals(List.of("%s"), placeholders(properties.getProperty("about.remaining.read.only")), bundle);
            assertEquals(List.of("%s"), placeholders(properties.getProperty("about.updates.until")), bundle);
            assertEquals(List.of("%s"), placeholders(properties.getProperty("about.updates.ended")), bundle);
            assertEquals(List.of(), placeholders(properties.getProperty("about.remaining.perpetual")), bundle);
        }
    }
}
