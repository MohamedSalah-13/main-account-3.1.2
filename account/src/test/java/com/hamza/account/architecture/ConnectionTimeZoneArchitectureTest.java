package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every JDBC URL this application builds must say {@code connectionTimeZone=LOCAL}, and none
 * may say {@code serverTimezone=UTC} again.
 * <p>
 * The MySQL behind this application runs on the machine's own local time: {@code NOW()} and
 * every {@code DEFAULT CURRENT_TIMESTAMP} store local wall clock. Telling the driver the
 * server is UTC made it convert in both directions, and the damage was not one bug but three,
 * only one of which was visible:
 * <ul>
 *   <li>a column MySQL wrote read back late by the whole offset - a shift opened at 08:00
 *       displayed as 11:00;</li>
 *   <li>a column written with {@code setTimestamp(Timestamp.valueOf(...))} was stored early by
 *       the offset and read back late, so the two errors cancelled on screen while the value
 *       on disk - the one every report, view and {@code BETWEEN} actually reads - was wrong;</li>
 *   <li>the bounds of a range query were converted while the column they filter was not, so
 *       {@code UserShiftDao.calculateShiftSummary} counted a window three hours out of line
 *       with its own rows. Measured against real data, a Z-report missed 24% of the day's
 *       takings and would have shown the till short by that much.</li>
 * </ul>
 * The last of those is why this is pinned rather than left to review: the first two look like
 * cosmetic date bugs, and the third is money, and they all have the same one-word cause.
 * {@code V38__timestamp_timezone_correction.sql} repairs the rows the second one left behind,
 * so putting {@code serverTimezone=UTC} back would not merely reintroduce the bug - it would
 * make already-corrected rows wrong in the opposite direction.
 */
class ConnectionTimeZoneArchitectureTest {

    /** Both modules' sources, since the pool lives in controlsfx and two callers in account. */
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src", "main", "java"),
            Path.of("..", "controlsfx", "src", "main", "java"));

    private static List<Path> javaFiles() {
        var found = new ArrayList<Path>();
        for (Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void noSourceClaimsTheServerKeepsUtc() {
        var offenders = new TreeSet<String>();
        for (Path file : javaFiles()) {
            if (file.endsWith("ConnectionTimeZoneArchitectureTest.java")) continue;
            if (read(file).contains("serverTimezone")) offenders.add(file.toString());
        }
        assertTrue(offenders.isEmpty(),
                "serverTimezone=UTC is a false claim about a server that keeps local time, and it "
                        + "shifts reads, writes and range-query bounds in different directions. Use "
                        + "connectionTimeZone=LOCAL. Offending files: " + offenders);
    }

    @Test
    void everyJdbcUrlPinsTheConnectionTimeZone() {
        var missing = new TreeSet<String>();
        for (Path file : javaFiles()) {
            if (file.endsWith("ConnectionTimeZoneArchitectureTest.java")) continue;
            String source = read(file);
            if (source.contains("jdbc:mysql://") && !source.contains("connectionTimeZone=LOCAL")) {
                missing.add(file.toString());
            }
        }
        assertFalse(javaFiles().isEmpty(), "no sources were scanned - the roots are wrong");
        assertTrue(missing.isEmpty(),
                "a JDBC URL that does not pin connectionTimeZone=LOCAL will convert timestamps "
                        + "against a server that keeps local time. Files: " + missing);
    }
}
