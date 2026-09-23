package com.hamza.account.features.profitloss.statement;

import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The days a profit and loss statement covers, both included, and the days it is compared with.
 *
 * <p><b>The period before is counted in months when the period starts on the first of one</b>, and in
 * days otherwise. "The 1st to the 23rd of September" is set against the 1st to the 23rd of August - the
 * same days of the month - and not against the 23 days that end on the 31st of August, which is what a
 * count of days would give and which nobody means. A whole month is set against the whole month before
 * it, whatever either's length, so February is compared with all of January. A period that starts on
 * any other day - a week, the 10th to the 16th - is set against the same number of days straight
 * before it.</p>
 *
 * <p>The same period last year is the same dates a year earlier; the 29th of February lands on the
 * 28th. A period longer than a year would overlap itself a year back, so it goes back as many whole years
 * as it takes to end before this one starts - the same dates, never the same days twice.</p>
 */
public record ProfitLossPeriod(LocalDate from, LocalDate to) {

    /** A period built in code is a programming error when reversed; one typed in is refused by {@link #of}. */
    public ProfitLossPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("A period ends on or after its start: " + from + " > " + to);
        }
    }

    /**
     * A period from the pickers: either left empty, or the two the wrong way round, is a refusal with a
     * sentence rather than a stack trace.
     */
    public static ProfitLossPeriod of(LocalDate from, LocalDate to) throws UserValidationException {
        LanguageManager language = LanguageManager.getInstance();
        if (from == null || to == null) {
            throw new UserValidationException(language.getString("profitloss.error.period.required"));
        }
        if (from.isAfter(to)) {
            throw new UserValidationException(language.getString("profitloss.error.period.reversed"));
        }
        return new ProfitLossPeriod(from, to);
    }

    /** The period this one is compared with. */
    public ProfitLossPeriod previous(ComparisonBasis basis) {
        Objects.requireNonNull(basis, "basis");
        return switch (basis) {
            case SAME_PERIOD_LAST_YEAR -> previousByYears();
            case PREVIOUS_PERIOD -> from.getDayOfMonth() == 1 ? previousByMonths() : previousByDays();
        };
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        return !day.isBefore(from) && !day.isAfter(to);
    }

    private ProfitLossPeriod previousByYears() {
        int years = 1;
        while (!to.minusYears(years).isBefore(from)) {
            years++;
        }
        return new ProfitLossPeriod(from.minusYears(years), to.minusYears(years));
    }

    private ProfitLossPeriod previousByMonths() {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
        LocalDate start = from.minusMonths(months);
        boolean wholeMonths = to.equals(YearMonth.from(to).atEndOfMonth());
        LocalDate end = wholeMonths
                ? YearMonth.from(to.minusMonths(months)).atEndOfMonth()
                : to.minusMonths(months);
        return new ProfitLossPeriod(start, end);
    }

    private ProfitLossPeriod previousByDays() {
        long length = days();
        return new ProfitLossPeriod(from.minusDays(length), from.minusDays(1));
    }
}
