package com.hamza.account.features.about;

import com.hamza.account.features.about.AboutLicense.Tone;
import com.hamza.account.trial.TrialManager.TrialDisplayInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
                "about.license.missing");
        for (String bundle : new String[]{"messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(Path.of("..", "controlsfx", "src", "main", "resources", "i18n", bundle))) {
                properties.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
            for (String key : keys) {
                assertTrue(properties.containsKey(key), bundle + " has no " + key);
            }
        }
    }
}
