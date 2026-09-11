package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * What a balances list is narrowed by.
 * <p>
 * <b>Every one of these was either impossible or done in Java over the whole table.</b> The accounts
 * screen loaded {@code account_customer_totals} entire on every refresh and then filtered it with a
 * {@code FilteredList}: a text predicate over the loaded rows, and one checkbox for "show the zeros".
 * Its period filter did not exist at all - {@code accountTotalList} was called with two nulls from
 * the one place that called it, so {@code PartyLedgerSpec.totalsBetweenDatesSql()}, written and
 * documented for exactly this, had no caller in the application.
 * <p>
 * As with {@code PartyStatementFilter}: one record reaches the page, the count, the print and the
 * export, so an exported file cannot describe a different set of parties from the table.
 *
 * @param partyKind    which ledger
 * @param asOf         the balance is as at the end of this day. Today, normally; an earlier date
 *                     answers "who owed me what at the end of last month", which is the question a
 *                     reconciliation asks
 * @param periodFrom   the first day of the movement window, or {@code null} for no window. It
 *                     narrows the <em>movement</em> columns and never the balance - the same rule as
 *                     the statement's, and for the same reason: a balance is everything up to a day
 * @param state        which parties to keep, by what their account comes to
 * @param minBalance   smallest balance to show, or {@code null}
 * @param maxBalance   largest, or {@code null}
 * @param areaId       one area, or {@code null}. The column has been on the totals view all along
 *                     with no screen reading it
 * @param priceTierId  one customer price tier, or {@code null}. Meaningless for a supplier
 * @param overLimitOnly only parties whose balance has passed their credit limit. A condition, rather
 *                     than the hourly poll {@code CreditLimitSource} does
 * @param idleDays     only parties with no movement for this many days, or {@code null}
 * @param text         matched against the name and the telephone, or blank
 * @param page         zero-based page index
 * @param pageSize     rows per page
 */
public record PartyBalanceFilter(
        PartyKind partyKind,
        LocalDate asOf,
        LocalDate periodFrom,
        BalanceState state,
        BigDecimal minBalance,
        BigDecimal maxBalance,
        Integer areaId,
        Integer priceTierId,
        boolean overLimitOnly,
        Integer idleDays,
        String text,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 10_000;

    public PartyBalanceFilter {
        Objects.requireNonNull(partyKind, "partyKind");
        Objects.requireNonNull(asOf, "asOf");
        state = state == null ? BalanceState.ALL : state;
        if (periodFrom != null && periodFrom.isAfter(asOf)) {
            throw new IllegalArgumentException("periodFrom must not be after asOf");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("invalid page size: " + pageSize);
        }
        if (minBalance != null && maxBalance != null && minBalance.compareTo(maxBalance) > 0) {
            throw new IllegalArgumentException("minBalance must not exceed maxBalance");
        }
        if (idleDays != null && idleDays < 0) {
            throw new IllegalArgumentException("idleDays must not be negative");
        }
        text = text == null ? "" : text.strip();
    }

    /** Everyone who owes something today: what the screen opens on. */
    public static PartyBalanceFilter debtorsToday(PartyKind kind) {
        return new PartyBalanceFilter(kind, LocalDate.now(), null, BalanceState.DEBTOR,
                null, null, null, null, false, null, "", 0, DEFAULT_PAGE_SIZE);
    }

    public static PartyBalanceFilter allToday(PartyKind kind) {
        return new PartyBalanceFilter(kind, LocalDate.now(), null, BalanceState.ALL,
                null, null, null, null, false, null, "", 0, DEFAULT_PAGE_SIZE);
    }

    public int offset() {
        return Math.multiplyExact(page, pageSize);
    }

    /** One more than the page holds, which is how "is there another page" is answered. */
    public int queryLimit() {
        return pageSize + 1;
    }

    public PartyBalanceFilter onPage(int newPage) {
        return new PartyBalanceFilter(partyKind, asOf, periodFrom, state, minBalance, maxBalance,
                areaId, priceTierId, overLimitOnly, idleDays, text, newPage, pageSize);
    }

    public PartyBalanceFilter firstPageWithSize(int size) {
        return new PartyBalanceFilter(partyKind, asOf, periodFrom, state, minBalance, maxBalance,
                areaId, priceTierId, overLimitOnly, idleDays, text, 0, size);
    }

    /** The window the movement columns cover: the period if one is set, otherwise all of it. */
    public LocalDate movementFrom() {
        return periodFrom == null ? LocalDate.EPOCH : periodFrom;
    }

    /** Whether a credit limit is a thing this ledger has at all. Only a customer has one. */
    public boolean hasCreditLimit() {
        return partyKind == PartyKind.CUSTOMER;
    }
}
