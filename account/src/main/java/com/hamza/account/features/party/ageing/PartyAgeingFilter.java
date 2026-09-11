package com.hamza.account.features.party.ageing;

import com.hamza.account.features.events.PartyKind;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the ageing report is asked for.
 *
 * <p><b>{@code asOf} is not one filter among several.</b> An ageing report is always as at a
 * day - it is what decides both the balance and how overdue each invoice is - so it has no
 * "all" and cannot be left out. Every other field narrows which parties are listed.
 *
 * <p>One record reaches the page, the count, the summary and the export, so the four cannot
 * come to describe different sets. Same rule as {@code PartyBalanceFilter} and
 * {@code ItemsDao.catalogQuery}.
 *
 * @param kind           customers or suppliers
 * @param asOf           the day the report is as at; invoices written after it and payments
 *                       made after it are both ignored, or the two halves of the
 *                       reconciliation would answer different questions
 * @param areaId         one area, or null for every area
 * @param overdueOnly    only parties with something actually past its due date. It asks about
 *                       the four overdue bands and not about the balance - a party whose
 *                       balance is zero because an old unpaid invoice is offset by a newer
 *                       payment on account still has an overdue invoice, and is exactly who
 *                       opens this report
 * @param includeSettled whether to list parties who owe nothing and have nothing open. Off by
 *                       default: a debt report of a hundred and forty-five rows most of which
 *                       are zeros is one nobody reads
 * @param minimumBalance a floor on the balance, or null for no floor. Null and zero are
 *                       different states - see {@code Utils.setOptionalNumberFormatter}
 * @param text           name, telephone or id
 * @param page           zero-based
 * @param pageSize       rows per page
 */
public record PartyAgeingFilter(
        PartyKind kind,
        LocalDate asOf,
        Integer areaId,
        boolean overdueOnly,
        boolean includeSettled,
        BigDecimal minimumBalance,
        String text,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * The largest page anyone may ask for, and it is the export's limit rather than a screen's.
     * <p>
     * <b>It was 500, and the export asked for 10,000.</b> So every press of the export button
     * threw {@code invalid page size: 10000} out of this constructor and reached the user as a
     * reference code - the screen worked perfectly and the file could never be produced. Two
     * numbers that have to agree, in two files, with nothing tying them together;
     * {@code PartyAgeingFilterTest} is now what ties them.
     */
    public static final int MAX_PAGE_SIZE = PartyAgeingService.PRINT_LIMIT;

    public PartyAgeingFilter {
        if (kind == null) {
            throw new IllegalArgumentException("An ageing report is of customers or of suppliers");
        }
        if (asOf == null) {
            throw new IllegalArgumentException("An ageing report is always as at a day");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("invalid page size: " + pageSize);
        }
        text = text == null ? "" : text.trim();
    }

    /** Today's report of everyone who is behind. */
    public static PartyAgeingFilter today(PartyKind kind) {
        return new PartyAgeingFilter(kind, LocalDate.now(), null, false, false,
                null, "", 0, DEFAULT_PAGE_SIZE);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    public PartyAgeingFilter withPage(int newPage) {
        return new PartyAgeingFilter(kind, asOf, areaId, overdueOnly, includeSettled,
                minimumBalance, text, newPage, pageSize);
    }

    public PartyAgeingFilter withPageSize(int newPageSize) {
        return new PartyAgeingFilter(kind, asOf, areaId, overdueOnly, includeSettled,
                minimumBalance, text, page, newPageSize);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than the page holds, which is how "is there another page" is answered. */
    public int fetchSize() {
        return pageSize + 1;
    }

    /**
     * The text search as a {@code LIKE} pattern, with its own wildcards escaped.
     * <p>
     * A customer called "50%" is a customer, not a pattern - the same escaping
     * {@code MasterDataQuery.pattern()} does, with the same {@code ESCAPE '!'}.
     */
    public String pattern() {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    /** The id a numeric search means, or 0 - which matches no party. */
    public int textAsId() {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
