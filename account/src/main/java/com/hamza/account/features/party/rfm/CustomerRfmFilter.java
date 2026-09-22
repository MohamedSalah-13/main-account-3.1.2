package com.hamza.account.features.party.rfm;

import java.time.LocalDate;

/**
 * What the recency, frequency and value table is asked for: one record reaches the page, the summary
 * and the export, so the three cannot describe different sets.
 *
 * <p><b>The period and the text do two different things.</b> The period decides every figure and
 * every score. The text only finds a customer among the scored rows - it is applied after the
 * scores are worked out, so typing a name never moves anybody from one fifth to another.</p>
 *
 * @param from           the first day counted
 * @param to             the last day counted, and the day recency is measured to
 * @param text           part of a name, or blank for everybody
 * @param excludedParty  the customer cash sales land on, which is a bucket rather than a person and
 *                       is left out of the population; {@code 0} when no such setting is made
 * @param order          which figure the rows are listed by
 * @param page           zero-based
 * @param pageSize       rows on a page
 */
public record CustomerRfmFilter(LocalDate from, LocalDate to, String text, int excludedParty,
                                CustomerRfmOrder order, int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = CustomerRfmService.PRINT_LIMIT;

    /** Why a range cannot be scored, in an order a screen can translate. */
    public enum Problem {
        NONE, MISSING, REVERSED
    }

    public CustomerRfmFilter {
        Problem problem = problem(from, to);
        if (problem != Problem.NONE) {
            throw new IllegalArgumentException("not a period to score: " + from + " - " + to);
        }
        if (order == null) {
            throw new IllegalArgumentException("the rows are listed by some figure");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page " + page);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page size " + pageSize);
        }
        text = text == null ? "" : text.strip();
        excludedParty = Math.max(excludedParty, 0);
    }

    public static Problem problem(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Problem.MISSING;
        }
        return from.isAfter(to) ? Problem.REVERSED : Problem.NONE;
    }

    /** The twelve months to today, from the first of the month eleven months back - the profile's default. */
    public static CustomerRfmFilter lastTwelveMonths(LocalDate today, int excludedParty) {
        return new CustomerRfmFilter(today.withDayOfMonth(1).minusMonths(11), today, "", excludedParty,
                CustomerRfmOrder.SCORE, 0, DEFAULT_PAGE_SIZE);
    }

    public CustomerRfmFilter withPage(int page) {
        return new CustomerRfmFilter(from, to, text, excludedParty, order, page, pageSize);
    }

    public CustomerRfmFilter withPageSize(int pageSize) {
        return new CustomerRfmFilter(from, to, text, excludedParty, order, page, pageSize);
    }

    /** One row more than the page holds: the extra row is what says another page follows. */
    public int fetchSize() {
        return pageSize + 1;
    }

    public int offset() {
        return page * pageSize;
    }

    /** The wildcard-escaped pattern the text is bound as, the balances screen's own escaping. */
    public String pattern() {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
