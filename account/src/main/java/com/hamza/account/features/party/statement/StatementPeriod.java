package com.hamza.account.features.party.statement;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * The periods a statement offers as a single click.
 * <p>
 * A date range is the filter every screen here starts from, and typing two dates to answer "what
 * happened this month" is the kind of friction a user pays on every visit. The treasury statement
 * opens on the current month for the same reason; these are that idea, named.
 * <p>
 * <b>It is an enum with a pure function rather than eight buttons wiring up eight date pairs.</b>
 * Off-by-one errors in period arithmetic are invisible on screen — a quarter that starts in the
 * wrong month still looks like a quarter — and they move money between reports. {@code to} is
 * computed from a supplied {@code today} so {@code StatementPeriodTest} can ask about a leap day, a
 * January, and the first of a month without waiting for one.
 * <p>
 * Every range ends no later than {@code today}: a statement of the future is a statement of
 * nothing, and a period picker that offers one invites a reader to wonder what is missing.
 */
public enum StatementPeriod {

    TODAY("party.period.today"),
    THIS_WEEK("party.period.this.week"),
    THIS_MONTH("party.period.this.month"),
    LAST_MONTH("party.period.last.month"),
    THIS_QUARTER("party.period.this.quarter"),
    THIS_YEAR("party.period.this.year"),

    /**
     * Everything, which for a statement means from the party's first movement.
     * <p>
     * {@link #from} cannot answer that on its own — it is a question for the database — so it
     * returns {@link LocalDate#EPOCH} and the caller substitutes the party's earliest movement.
     * {@link #needsEarliestMovement()} is how a caller knows to.
     */
    ALL("party.period.all");

    private final String messageKey;

    StatementPeriod(String messageKey) {
        this.messageKey = messageKey;
    }

    /** The key the screen translates. Never compared against anything. */
    public String messageKey() {
        return messageKey;
    }

    /**
     * Whether the caller must replace {@link #from} with the party's first movement.
     * <p>
     * Only {@link #ALL} does, and the alternative would be a hard-coded early date that prints as
     * the opening of every statement — "from 1970" on a page a customer is asked to agree with.
     */
    public boolean needsEarliestMovement() {
        return this == ALL;
    }

    /** The first day of this period as at {@code today}. */
    public LocalDate from(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return switch (this) {
            case TODAY -> today;
            // The week runs Saturday to Friday: this is an Arabic-market application, and a week
            // that starts on Monday reports Saturday's and Sunday's takings in the week before.
            case THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY));
            case THIS_MONTH -> today.withDayOfMonth(1);
            case LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1);
            case THIS_QUARTER -> today.withMonth(firstMonthOfQuarter(today)).withDayOfMonth(1);
            case THIS_YEAR -> today.withDayOfYear(1);
            case ALL -> LocalDate.EPOCH;
        };
    }

    /**
     * The last day, never later than {@code today}.
     * <p>
     * {@link #LAST_MONTH} is the one that ends earlier: its own last day. Everything else runs up to
     * today, because a period that ended yesterday hides a sale made this morning.
     */
    public LocalDate to(LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (this == LAST_MONTH) {
            return today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        }
        return today;
    }

    private static int firstMonthOfQuarter(LocalDate today) {
        return ((today.getMonthValue() - 1) / 3) * 3 + 1;
    }
}
