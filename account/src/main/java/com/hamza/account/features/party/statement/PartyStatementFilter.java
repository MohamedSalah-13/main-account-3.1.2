package com.hamza.account.features.party.statement;

import com.hamza.account.features.events.PartyKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Everything a statement is narrowed by, in one value.
 * <p>
 * Modelled on {@code TreasuryStatementFilter} deliberately: one record is passed to the
 * page query, to the summary, to the print extract and to the export, so the file a user
 * saves cannot describe a different set of rows from the one on screen. That is the trap
 * {@code ItemsDao.catalogQuery} records — a page and its {@code COUNT} built from two
 * different {@code WHERE} clauses — and the reason the export button on the totals screen
 * is a bug today: it exports the ticked rows while its emptiness check asks the table.
 * <p>
 * <b>The rule that is easiest to get wrong when adding a filter here.</b> The filters
 * narrow <em>which rows are shown</em>. The opening balance carried into the period is a
 * function of {@link #from()} and of nothing else — see
 * {@link PartyStatementSummary#openingBalance()}. A "balance before the period" computed
 * over, say, payments only is not a balance; it is a number with no meaning that a
 * customer would be asked to sign. {@code PartyStatementTest} pins it, and a new filter
 * belongs in {@link #narrowsRows()} rather than anywhere near the opening query.
 *
 * @param partyKind which ledger is being read. Chooses the view and the permission
 * @param partyId   whose statement this is. Required: a statement belongs to one party,
 *                  and the running balance is seeded from that party's own history. "What
 *                  does everyone owe" is a different question with its own view,
 *                  {@code account_customer_totals}
 * @param from      first day shown. Required: the opening balance is measured against it
 * @param to        last day shown, inclusive
 * @param kinds     which movement kinds to show; empty means all of them
 * @param treasuryId one till, or {@code null} for all. Answers "what came in on the wallet"
 * @param userId    who entered the movement, or {@code null} for all
 * @param minAmount smallest absolute balance change to show, or {@code null}
 * @param maxAmount largest absolute balance change to show, or {@code null}
 * @param text      matched against the notes and the reference number, or blank
 * @param deferredOnly show only documents that were not settled in cash
 * @param page      zero-based page index
 * @param pageSize  rows per page, 1..10000
 */
public record PartyStatementFilter(
        PartyKind partyKind,
        int partyId,
        LocalDate from,
        LocalDate to,
        Set<PartyMovementKind> kinds,
        Integer treasuryId,
        Integer userId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String text,
        boolean deferredOnly,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 100;

    /** As on the treasury side: one screenful of a report, not a whole ledger in memory. */
    public static final int MAX_PAGE_SIZE = 10_000;

    public PartyStatementFilter {
        Objects.requireNonNull(partyKind, "partyKind");
        Objects.requireNonNull(from, "from");
        if (partyId <= 0) {
            throw new IllegalArgumentException("partyId must identify a party: " + partyId);
        }
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("invalid page size: " + pageSize);
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must not exceed maxAmount");
        }
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        text = text == null ? "" : text.strip();
    }

    /** One party's whole history up to today, unfiltered. The statement screen's default. */
    public static PartyStatementFilter wholeHistory(PartyKind kind, int partyId, LocalDate earliest) {
        return new PartyStatementFilter(kind, partyId, earliest, LocalDate.now(),
                Set.of(), null, null, null, null, "", false, 0, DEFAULT_PAGE_SIZE);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than the page holds, which is how "is there a next page" is answered. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public PartyStatementFilter firstPageWithSize(int size) {
        return new PartyStatementFilter(partyKind, partyId, from, to, kinds, treasuryId, userId,
                minAmount, maxAmount, text, deferredOnly, 0, size);
    }

    public PartyStatementFilter onPage(int newPage) {
        return new PartyStatementFilter(partyKind, partyId, from, to, kinds, treasuryId, userId,
                minAmount, maxAmount, text, deferredOnly, newPage, pageSize);
    }

    /**
     * Whether anything beyond the period is hiding rows.
     * <p>
     * The screen says so next to the totals: a period total under an active filter is a
     * total of what is shown, and a reader who cannot see that the list is narrowed will
     * read it as the period's.
     */
    public boolean narrowsRows() {
        return !kinds.isEmpty() || treasuryId != null || userId != null
                || minAmount != null || maxAmount != null || !text.isEmpty() || deferredOnly;
    }

    /** The codes the query binds, or null when every kind is wanted. */
    public String kindCodes() {
        if (kinds.isEmpty()) {
            return null;
        }
        return kinds.stream().map(kind -> String.valueOf(kind.code())).sorted()
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }
}
