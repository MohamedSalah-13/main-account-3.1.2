package com.hamza.account.period;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Where the accounting line currently sits: everything dated on or before
 * {@link #lockedUntil} is closed.
 * <p>
 * {@code lockedUntil} may be null, which is what re-opening writes and what a database
 * that has never closed anything answers. {@link #OPEN} is that state.
 * <p>
 * The decision is a pure function of a date and this record, which is why it lives here
 * and not in a service: the boundary - is a document dated <em>on</em> the closing date
 * closed or open - is the part that goes wrong by one, and it can be pinned down
 * without a database.
 *
 * @param lockedUntil the last closed day, or null if nothing is closed
 * @param notes       why it was closed, free text
 * @param closedAt    when the decision was recorded
 * @param userId      who recorded it
 */
public record AccountingLock(LocalDate lockedUntil, String notes, LocalDateTime closedAt, int userId) {

    /** Nothing is closed. What a database with no rows in {@code accounting_lock} means. */
    public static final AccountingLock OPEN = new AccountingLock(null, "", null, 1);

    public AccountingLock {
        notes = notes == null ? "" : notes;
    }

    public boolean isClosed() {
        return lockedUntil != null;
    }

    /**
     * Whether a document dated {@code date} falls inside the closed period.
     * <p>
     * The closing date itself is <b>closed</b>: "مغلق حتى 31 ديسمبر" has to include the
     * thirty-first, or closing a year would leave its last day editable. A document
     * with no date at all is treated as open - refusing it would block a save over a
     * missing value rather than over a period, and say the wrong thing while doing it.
     */
    public boolean covers(LocalDate date) {
        return isClosed() && date != null && !date.isAfter(lockedUntil);
    }

    /** The first day still open, or null while nothing is closed. */
    public LocalDate firstOpenDay() {
        return isClosed() ? lockedUntil.plusDays(1) : null;
    }
}
