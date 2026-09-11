package com.hamza.account.features.party.ageing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where each band begins and ends.
 * <p>
 * Boundaries, because that is the whole of it: an off-by-one here moves money between two
 * columns a manager reads side by side, and both columns still look plausible.
 */
class AgeingBucketTest {

    @Nested
    @DisplayName("Which band a debt falls in")
    class Bands {

        @ParameterizedTest(name = "{0} days overdue is {1}")
        @CsvSource({
                "-100, CURRENT",
                "-1,   CURRENT",
                "0,    CURRENT",
                "1,    DAYS_1_30",
                "30,   DAYS_1_30",
                "31,   DAYS_31_60",
                "60,   DAYS_31_60",
                "61,   DAYS_61_90",
                "90,   DAYS_61_90",
                "91,   OVER_90",
                "3650, OVER_90"
        })
        void boundaries(long daysOverdue, AgeingBucket expected) {
            assertEquals(expected, AgeingBucket.of(daysOverdue));
        }

        /**
         * Day zero is the due date itself, and a debt due today is not overdue. Putting it in
         * 1-30 would report every invoice as late the moment it fell due.
         */
        @Test
        @DisplayName("the due date itself is not yet overdue")
        void dueTodayIsCurrent() {
            assertEquals(AgeingBucket.CURRENT, AgeingBucket.of(0));
            assertFalse(AgeingBucket.CURRENT.isOverdue());
        }

        @Test
        @DisplayName("every other band is overdue")
        void theOtherFourAreOverdue() {
            for (AgeingBucket bucket : AgeingBucket.values()) {
                assertEquals(bucket != AgeingBucket.CURRENT, bucket.isOverdue(), bucket.name());
            }
        }

        /** No gap and no overlap: every whole number of days lands in exactly one band. */
        @ParameterizedTest(name = "day {0} belongs to exactly one band")
        @ValueSource(longs = {-5, 0, 1, 15, 30, 31, 45, 60, 61, 75, 90, 91, 200})
        void everyDayHasExactlyOneBand(long daysOverdue) {
            AgeingBucket found = AgeingBucket.of(daysOverdue);
            assertNotNull(found);
            long matches = List.of(AgeingBucket.values()).stream()
                    .filter(bucket -> daysOverdue >= bucket.from() && daysOverdue <= bucket.to())
                    .count();
            assertEquals(1, matches, "day " + daysOverdue + " matched " + matches + " bands");
        }
    }

    @Nested
    @DisplayName("The order the report reads in")
    class Order {

        @Test
        void oldestLast() {
            assertEquals(List.of(AgeingBucket.CURRENT, AgeingBucket.DAYS_1_30,
                            AgeingBucket.DAYS_31_60, AgeingBucket.DAYS_61_90, AgeingBucket.OVER_90),
                    List.of(AgeingBucket.inReadingOrder()));
        }

        /** Every band appears, or a column of the report would hold money nothing shows. */
        @Test
        void nothingIsLeftOut() {
            assertEquals(AgeingBucket.values().length, AgeingBucket.inReadingOrder().length);
            for (AgeingBucket bucket : AgeingBucket.values()) {
                assertTrue(List.of(AgeingBucket.inReadingOrder()).contains(bucket), bucket.name());
            }
        }
    }

    /**
     * The five captions are resolved through a variable in the screen and the export, so
     * {@code MessageKeyArchitectureTest} cannot see them - the same reason
     * {@code PartyStatementTest} checks {@code PartyMovementKind}'s keys here instead.
     */
    @Nested
    @DisplayName("Every band has a caption in all three bundles")
    class Captions {

        @Test
        void inEveryBundle() throws IOException {
            for (String bundle : new String[]{"messages", "messages_ar", "messages_en"}) {
                Properties properties = load(bundle);
                for (AgeingBucket bucket : AgeingBucket.values()) {
                    String key = bucket.messageKey();
                    assertTrue(properties.containsKey(key),
                            key + " is missing from " + bundle + ".properties");
                    assertFalse(properties.getProperty(key).isBlank(),
                            key + " is blank in " + bundle + ".properties");
                }
            }
        }

        @Test
        @DisplayName("and the keys are distinct, so two bands cannot share a caption")
        void distinct() {
            long distinct = List.of(AgeingBucket.values()).stream()
                    .map(AgeingBucket::messageKey).distinct().count();
            assertEquals(AgeingBucket.values().length, distinct);
        }

        /**
         * Read off disk, not as a resource bundle: the properties belong to {@code controlsfx}
         * and are not on this module's test classpath - the same reason
         * {@code PartyStatementTest} reads them this way.
         */
        private Properties load(String bundle) throws IOException {
            Path file = Path.of("..", "controlsfx", "src", "main", "resources", "i18n",
                    bundle + ".properties");
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            return properties;
        }
    }
}
