package com.hamza.account.features.about;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AboutBuildTest {

    private static AboutBuild of(String version, String built, String zone) {
        Properties properties = new Properties();
        if (version != null) {
            properties.setProperty("app.version", version);
        }
        if (built != null) {
            properties.setProperty("build.date", built);
        }
        return AboutBuild.of(properties, ZoneId.of(zone));
    }

    @Test
    @DisplayName("the version and the day Maven stamped, on this computer's calendar")
    void stamped() {
        AboutBuild build = of("4.11.0", "2026-09-23T22:30:00Z", "Africa/Cairo");
        assertEquals(Optional.of("4.11.0"), build.version());
        assertEquals(Optional.of(LocalDate.of(2026, 9, 24)), build.built(), "half past one in the morning in Cairo");
        assertEquals(2026, build.copyrightYear(LocalDate.of(2030, 1, 1)));
    }

    @Test
    @DisplayName("the format the pom declares, and a bare day, are read too")
    void otherFormats() {
        assertEquals(Optional.of(LocalDate.of(2026, 9, 24)), of("4", "2026-09-24_04-46-00", "UTC").built());
        assertEquals(Optional.of(LocalDate.of(2026, 9, 24)), of("4", "2026-09-24", "UTC").built());
    }

    @Test
    @DisplayName("a value the resource filter never reached is no value, and the copyright runs to this year")
    void unfiltered() {
        AboutBuild build = of("${project.version}", "${maven.build.timestamp}", "UTC");
        assertEquals(Optional.empty(), build.version());
        assertEquals(Optional.empty(), build.built());
        assertEquals(2031, build.copyrightYear(LocalDate.of(2031, 5, 1)));
        assertEquals(Optional.empty(), of(null, "yesterday", "UTC").built());
    }
}
