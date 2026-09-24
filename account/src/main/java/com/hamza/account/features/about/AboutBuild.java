package com.hamza.account.features.about;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

/**
 * Which build this is, as {@code version.properties} says: the version Maven stamped and the day it
 * was built.
 * <p>
 * The About screen used to show {@code PropertiesName.getAppLastRunVersion()} as the version - the
 * version this computer last <i>ran</i>, a preference that answers {@code 1.0.0} until it is first
 * written - and the build's timestamp as Maven wrote it, {@code 2026-09-24T04:46:00Z}, inside an
 * Arabic sentence that moved its parts about.
 *
 * @param version the build's version, or empty for a build the resource filter never ran over
 * @param built   the day it was built on this computer's calendar, or empty likewise
 */
public record AboutBuild(Optional<String> version, Optional<LocalDate> built) {

    private static final DateTimeFormatter MAVEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static AboutBuild current() {
        Properties properties = new Properties();
        try (InputStream input = AboutBuild.class.getResourceAsStream("/version.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException unreadable) {
            return new AboutBuild(Optional.empty(), Optional.empty());
        }
        return of(properties, ZoneId.systemDefault());
    }

    static AboutBuild of(Properties properties, ZoneId zone) {
        return new AboutBuild(stamped(properties.getProperty("app.version")),
                stamped(properties.getProperty("build.date")).flatMap(value -> day(value, zone)));
    }

    /** The year the copyright runs to: the build's, or this one for a build that says nothing. */
    public int copyrightYear(LocalDate today) {
        return built.map(LocalDate::getYear).orElse(today.getYear());
    }

    /** A value the resource filter replaced; {@code ${...}} is one it never reached. */
    private static Optional<String> stamped(String value) {
        return value == null || value.isBlank() || value.contains("${") ? Optional.empty() : Optional.of(value.strip());
    }

    private static Optional<LocalDate> day(String value, ZoneId zone) {
        try {
            return Optional.of(Instant.parse(value).atZone(zone).toLocalDate());
        } catch (DateTimeParseException notAnInstant) {
            // the format the pom declares for the jar's manifest
        }
        try {
            return Optional.of(LocalDateTime.parse(value, MAVEN_FORMAT).toLocalDate());
        } catch (DateTimeParseException notThatEither) {
            // a bare day, or nothing that can be read as one
        }
        try {
            return Optional.of(LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value));
        } catch (DateTimeParseException unreadable) {
            return Optional.empty();
        }
    }
}
