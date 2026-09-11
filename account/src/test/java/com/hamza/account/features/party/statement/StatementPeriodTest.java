package com.hamza.account.features.party.statement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The period presets, asked about on days a developer would not happen to be working.
 * <p>
 * Period arithmetic is worth a test precisely because it is invisible when wrong: a quarter that
 * starts in the wrong month still looks like a quarter on screen, and the figures it reports are
 * somebody's receivables.
 */
class StatementPeriodTest {

    /** A Wednesday in the middle of a month, in the middle of a quarter. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);

    @Test
    void todayIsOneDay() {
        assertEquals(WEDNESDAY, StatementPeriod.TODAY.from(WEDNESDAY));
        assertEquals(WEDNESDAY, StatementPeriod.TODAY.to(WEDNESDAY));
    }

    /**
     * The week runs Saturday to Friday.
     * <p>
     * Not Monday: this is an Arabic-market application, and a week that starts on Monday reports
     * Saturday's and Sunday's takings in the week before - two of the busiest days of a shop's week,
     * in the wrong column.
     */
    @Test
    @DisplayName("the week starts on Saturday, not Monday")
    void theWeekStartsOnSaturday() {
        LocalDate from = StatementPeriod.THIS_WEEK.from(WEDNESDAY);
        assertEquals(DayOfWeek.SATURDAY, from.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 8, 8), from);
    }

    /** On a Saturday the week starts today, not seven days ago. */
    @Test
    void onASaturdayTheWeekStartsToday() {
        LocalDate saturday = LocalDate.of(2026, 8, 8);
        assertEquals(saturday, StatementPeriod.THIS_WEEK.from(saturday));
    }

    @Test
    void thisMonthStartsOnTheFirst() {
        assertEquals(LocalDate.of(2026, 8, 1), StatementPeriod.THIS_MONTH.from(WEDNESDAY));
        assertEquals(WEDNESDAY, StatementPeriod.THIS_MONTH.to(WEDNESDAY));
    }

    /** The one period that ends before today: last month ends on its own last day. */
    @Test
    @DisplayName("last month is the whole of last month, not the same days of it")
    void lastMonthIsWholeAndEndsEarly() {
        assertEquals(LocalDate.of(2026, 7, 1), StatementPeriod.LAST_MONTH.from(WEDNESDAY));
        assertEquals(LocalDate.of(2026, 7, 31), StatementPeriod.LAST_MONTH.to(WEDNESDAY));
    }

    /** In January, last month is December of the year before. */
    @Test
    void lastMonthCrossesTheYear() {
        LocalDate january = LocalDate.of(2026, 1, 15);
        assertEquals(LocalDate.of(2025, 12, 1), StatementPeriod.LAST_MONTH.from(january));
        assertEquals(LocalDate.of(2025, 12, 31), StatementPeriod.LAST_MONTH.to(january));
    }

    /** On the 31st, last month may be shorter than this one. */
    @Test
    void lastMonthFromTheThirtyFirstOfAMonthWithThirty() {
        LocalDate march31 = LocalDate.of(2026, 3, 31);
        assertEquals(LocalDate.of(2026, 2, 1), StatementPeriod.LAST_MONTH.from(march31));
        assertEquals(LocalDate.of(2026, 2, 28), StatementPeriod.LAST_MONTH.to(march31));
    }

    @Test
    void lastMonthHandlesALeapFebruary() {
        LocalDate march31 = LocalDate.of(2028, 3, 31);
        assertEquals(LocalDate.of(2028, 2, 29), StatementPeriod.LAST_MONTH.to(march31));
    }

    /** The four quarters start in January, April, July and October - and only those. */
    @Test
    @DisplayName("a quarter starts in January, April, July or October")
    void quartersStartWhereQuartersStart() {
        assertEquals(1, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 1, 5)).getMonthValue());
        assertEquals(1, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 3, 31)).getMonthValue());
        assertEquals(4, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 4, 1)).getMonthValue());
        assertEquals(4, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 6, 30)).getMonthValue());
        assertEquals(7, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 7, 1)).getMonthValue());
        assertEquals(7, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 9, 9)).getMonthValue());
        assertEquals(10, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 10, 1)).getMonthValue());
        assertEquals(10, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, 12, 31)).getMonthValue());
        for (int month = 1; month <= 12; month++) {
            assertEquals(1, StatementPeriod.THIS_QUARTER.from(LocalDate.of(2026, month, 15)).getDayOfMonth());
        }
    }

    @Test
    void thisYearStartsOnTheFirstOfJanuary() {
        assertEquals(LocalDate.of(2026, 1, 1), StatementPeriod.THIS_YEAR.from(WEDNESDAY));
    }

    /**
     * No preset ever reaches past today.
     * <p>
     * A statement of the future is a statement of nothing, and a period picker that offers one
     * invites the reader to wonder what is missing from it.
     */
    @ParameterizedTest
    @EnumSource(StatementPeriod.class)
    void noPeriodRunsPastToday(StatementPeriod period) {
        assertFalse(period.to(WEDNESDAY).isAfter(WEDNESDAY), period + " ends after today");
    }

    /** And none runs backwards, on any day of a year. */
    @ParameterizedTest
    @EnumSource(StatementPeriod.class)
    void noPeriodRunsBackwards(StatementPeriod period) {
        LocalDate day = LocalDate.of(2028, 1, 1);
        for (int i = 0; i < 366; i++) {
            LocalDate today = day.plusDays(i);
            assertFalse(period.from(today).isAfter(period.to(today)),
                    period + " runs backwards on " + today);
        }
    }

    /**
     * Only {@code ALL} asks the caller for the party's first movement.
     * <p>
     * The alternative is a hard-coded early date, which prints as the opening of every statement -
     * "from 1970" on a page a customer is asked to agree with.
     */
    @ParameterizedTest
    @EnumSource(StatementPeriod.class)
    void onlyAllNeedsTheEarliestMovement(StatementPeriod period) {
        assertEquals(period == StatementPeriod.ALL, period.needsEarliestMovement());
        if (period.needsEarliestMovement()) {
            assertEquals(LocalDate.EPOCH, period.from(WEDNESDAY));
        }
    }

    /** Each preset's caption is translated in all three bundles; the screen resolves it by variable. */
    @ParameterizedTest
    @EnumSource(StatementPeriod.class)
    void everyPeriodIsTranslatedInEveryBundle(StatementPeriod period) {
        Path dir = Path.of("..", "controlsfx", "src", "main", "resources", "i18n");
        for (String bundle : new String[]{
                "messages.properties", "messages_ar.properties", "messages_en.properties"}) {
            Properties properties = read(dir.resolve(bundle));
            String value = properties.getProperty(period.messageKey());
            assertNotNull(value, period.messageKey() + " is missing from " + bundle);
            assertFalse(value.isBlank(), period.messageKey() + " is blank in " + bundle);
        }
    }

    @Test
    void aPeriodRefusesANullDay() {
        for (StatementPeriod period : StatementPeriod.values()) {
            assertTrue(assertThrowsNpe(() -> period.from(null)));
            assertTrue(assertThrowsNpe(() -> period.to(null)));
        }
    }

    private static boolean assertThrowsNpe(Runnable action) {
        try {
            action.run();
            return false;
        } catch (NullPointerException expected) {
            return true;
        }
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        return properties;
    }
}
