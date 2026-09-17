package com.hamza.account.features.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What the expenses list is narrowed by.
 * <p>
 * The list it replaces could be narrowed by nothing: it read the whole table, searched fifty rows at a
 * time, and totalled whatever happened to be on screen (docs/expenses-plan.md ع-٦). One record now
 * reaches the page, the summary, the print and the export, so none of them can describe a different
 * set of expenses from the table.
 *
 * @param from       the first day, or {@code null} for no lower bound
 * @param to         the last day, or {@code null}
 * @param headingId  one heading - <b>a main heading includes every heading under it</b> - or {@code null}
 * @param treasuryId one till, or {@code null}
 * @param userId     who entered it, or {@code null}
 * @param minAmount  no bound when {@code null}; a bound of zero stays a bound somebody asked for
 * @param maxAmount  the same
 * @param text       matched against the code, the heading, the payee, the reference, the notes and the
 *                   employee
 * @param page       zero-based
 */
public record ExpenseFilter(LocalDate from,
                            LocalDate to,
                            Integer headingId,
                            Integer treasuryId,
                            Integer userId,
                            BigDecimal minAmount,
                            BigDecimal maxAmount,
                            String text,
                            int page,
                            int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 10_000;

    public ExpenseFilter {
        text = text == null ? "" : text.strip();
        headingId = positiveOrNull(headingId);
        treasuryId = positiveOrNull(treasuryId);
        userId = positiveOrNull(userId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("minAmount must not be above maxAmount");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    /** Everything in a period, on its first page. */
    public static ExpenseFilter between(LocalDate from, LocalDate to) {
        return new ExpenseFilter(from, to, null, null, null, null, null, "", 0, DEFAULT_PAGE_SIZE);
    }

    public boolean hasText() {
        return !text.isEmpty();
    }

    /** The text as a code, or -1 when it is not one. Never throws: a long run of digits is a reference. */
    public int numericText() {
        if (!hasText() || !text.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException overflow) {
            return -1;
        }
    }

    /** One row more than the page holds - what answers "is there another page" with no second count. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public int offset() {
        return page * pageSize;
    }

    public ExpenseFilter onPage(int index) {
        return new ExpenseFilter(from, to, headingId, treasuryId, userId, minAmount, maxAmount, text,
                index, pageSize);
    }

    public ExpenseFilter firstPageWithSize(int size) {
        return new ExpenseFilter(from, to, headingId, treasuryId, userId, minAmount, maxAmount, text, 0, size);
    }

    /** The same conditions over other dates - a report's row or cell opening the list on its own period. */
    public ExpenseFilter withPeriod(LocalDate newFrom, LocalDate newTo) {
        return new ExpenseFilter(newFrom, newTo, headingId, treasuryId, userId, minAmount, maxAmount, text, 0,
                pageSize);
    }

    /** The same conditions on one heading - a main heading still includes every heading under it. */
    public ExpenseFilter withHeading(Integer heading) {
        return new ExpenseFilter(from, to, heading, treasuryId, userId, minAmount, maxAmount, text, 0, pageSize);
    }

    public ExpenseFilter withTreasury(Integer treasury) {
        return new ExpenseFilter(from, to, headingId, treasury, userId, minAmount, maxAmount, text, 0, pageSize);
    }

    public ExpenseFilter withUser(Integer user) {
        return new ExpenseFilter(from, to, headingId, treasuryId, user, minAmount, maxAmount, text, 0, pageSize);
    }

    /**
     * The period of the same length just before this one, with every other condition kept - or
     * {@code null} when this filter has no whole period to measure against.
     * <p>
     * "The same length" in days, not in calendar months: the 1st to the 17th of September is compared
     * with the 15th to the 31st of August, which is the only comparison that is fair to a month in
     * progress. A month against the whole previous month would report every month as a fall until its
     * last day.
     */
    public ExpenseFilter previousPeriod() {
        if (from == null || to == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousTo = from.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1);
        return new ExpenseFilter(previousFrom, previousTo, headingId, treasuryId, userId, minAmount,
                maxAmount, text, 0, pageSize);
    }

    /**
     * How many of the conditions behind the filters button narrow the list. The period and the text sit
     * in the bar in plain view and are not counted: the period says <i>which</i> list this is, and a
     * closed panel must never hide that.
     */
    public int panelConditionCount() {
        int count = 0;
        if (headingId != null) count++;
        if (treasuryId != null) count++;
        if (userId != null) count++;
        if (minAmount != null) count++;
        if (maxAmount != null) count++;
        return count;
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExpenseFilter f
                && Objects.equals(from, f.from) && Objects.equals(to, f.to)
                && Objects.equals(headingId, f.headingId) && Objects.equals(treasuryId, f.treasuryId)
                && Objects.equals(userId, f.userId)
                && sameAmount(minAmount, f.minAmount) && sameAmount(maxAmount, f.maxAmount)
                && text.equals(f.text) && page == f.page && pageSize == f.pageSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, headingId, treasuryId, userId,
                minAmount == null ? null : minAmount.stripTrailingZeros(),
                maxAmount == null ? null : maxAmount.stripTrailingZeros(), text, page, pageSize);
    }

    /** {@code 10} and {@code 10.00} are one bound; {@code BigDecimal.equals} says otherwise. */
    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
