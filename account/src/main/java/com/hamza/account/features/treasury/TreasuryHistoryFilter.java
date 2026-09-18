package com.hamza.account.features.treasury;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Which transfers, or which deposits and withdrawals, a history list shows.
 * <p>
 * The two screens used to show "the last fifty" and nothing else: a transfer made in March could
 * not be found in September except by scrolling a list that did not reach it, and could therefore
 * not be corrected either, since the delete acts on a row of that list. The period is what says
 * which list this is and stays in the bar; the treasury and the direction narrow it.
 * <p>
 * Built on {@code TreasuryStatementFilter}: the checks are in the constructor, so a filter that
 * exists is one the query can run.
 *
 * @param treasuryId {@code null} for every treasury; for a transfer, either end of it
 * @param direction  deposits or withdrawals alone; {@code null} for both, and ignored by transfers
 */
public record TreasuryHistoryFilter(LocalDate from, LocalDate to, Integer treasuryId,
                                    CashDirection direction, int page, int pageSize) {

    public static final int PAGE_SIZE = 50;
    /** The same boundary the treasury statement prints to. */
    public static final int PRINT_LIMIT = 10_000;

    public TreasuryHistoryFilter {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) throw new IllegalArgumentException("from must not be after to");
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (pageSize < 1 || pageSize > PRINT_LIMIT) throw new IllegalArgumentException("invalid page size");
    }

    /** The month so far - what a person opening the screen is most likely looking for. */
    public static TreasuryHistoryFilter thisMonth(LocalDate today) {
        return new TreasuryHistoryFilter(today.withDayOfMonth(1), today, null, null, 0, PAGE_SIZE);
    }

    /** How many conditions narrow the list beyond its period - shown on a closed filters panel. */
    public int panelConditionCount() {
        int count = 0;
        if (treasuryId != null) count++;
        if (direction != null) count++;
        return count;
    }

    public int offset() { return Math.multiplyExact(page, pageSize); }

    /** One more than a page, so "is there a next page" costs no second query. */
    public int queryLimit() { return pageSize + 1; }

    public TreasuryHistoryFilter onPage(int newPage) {
        return new TreasuryHistoryFilter(from, to, treasuryId, direction, newPage, pageSize);
    }

    /** The whole filtered set, for paper and for a file - never the page on screen. */
    public TreasuryHistoryFilter forPrint() {
        return new TreasuryHistoryFilter(from, to, treasuryId, direction, 0, PRINT_LIMIT);
    }
}
