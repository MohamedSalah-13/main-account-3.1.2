package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.party.PartyTableSpec;

/**
 * What every party owes, filtered, summed and paged in SQL.
 * <p>
 * <b>It reads the ledger view and groups it, rather than reading {@code account_customer_totals}.</b>
 * The totals view has already summed everything there has ever been, and a total cannot be filtered
 * after the fact - which is the note {@code PartyLedgerSpec.totalsBetweenDatesSql()} carries, and the
 * reason a dated summary has to be its own statement. Grouping here gives the period window, the
 * balance as at a chosen day, and every other filter in one statement that can also be paged. The
 * arithmetic is the same as the totals view's, so the two agree by construction;
 * {@code PartyBalanceViewAcceptanceTest} holds them to it.
 * <p>
 * <b>The balance and the movement answer different windows, deliberately.</b> {@code balance} sums
 * everything up to {@code asOf} - that is what a balance is, and narrowing it by a period produces a
 * number nobody owes. {@code period_debit} and {@code period_credit} cover the window. The same split
 * as {@code PartyStatementQuery.summarySql}, for the same reason, and the one a later filter is most
 * likely to break.
 * <p>
 * Only identifiers the two specifications own are concatenated; every user value is bound, and the
 * text search escapes its wildcards with {@code ESCAPE '!'}. {@code PartyBalanceQueryTest} pins the
 * statements and their parameter counts.
 */
public final class PartyBalanceQuery {

    /** What the party's account comes to: the expression both the select and the HAVING use. */
    private static final String BALANCE =
            "ROUND(SUM(CASE WHEN m.account_date <= ? THEN m.purchase - m.discount - m.paid ELSE 0 END), 2)";

    private PartyBalanceQuery() {
    }

    /**
     * One page of the balances list.
     * <p>
     * Parameters in order: {@code asOf} for the balance, the period's two bounds for each of the two
     * movement totals, {@code asOf} again to exclude the future from the last-movement date, then the
     * row filters - area, price tier, text ×3 - then the credit-limit condition, the balance range,
     * the idle-days cut-off, and finally the limit and the offset.
     * <p>
     * The balance-range and balance-state conditions are in the {@code HAVING}, which is where they
     * belong: they compare against an aggregate. (Unlike {@code OpenInvoiceQuery}, where the filter
     * compares two plain columns and a {@code HAVING} would have been MySQL-specific sloppiness.)
     */
    public static String pageSql(PartyBalanceFilter filter) {
        return select(filter) + havingSql(filter)
                + "\nORDER BY balance DESC, p." + PartyTableSpec.NAME
                + "\nLIMIT ? OFFSET ?";
    }

    /**
     * How many parties the filter matches, and what they come to in total.
     * <p>
     * One statement rather than a {@code COUNT} and a {@code SUM}, and built from the same
     * {@code select} the page is - so the footer cannot describe a different set from the table. That
     * is the {@code ItemsDao.catalogQuery} rule: a page and its count from two {@code WHERE}s drift,
     * and the pagination control starts describing rows nobody can see.
     */
    public static String summarySql(PartyBalanceFilter filter) {
        return """
                SELECT COUNT(*)                                         AS parties,
                       COALESCE(SUM(grouped.balance), 0)                AS total_balance,
                       COALESCE(SUM(GREATEST(grouped.balance, 0)), 0)   AS total_owed,
                       COALESCE(SUM(GREATEST(-grouped.balance, 0)), 0)  AS total_in_credit,
                       COALESCE(SUM(CASE WHEN grouped.credit_limit > 0
                                          AND grouped.balance > grouped.credit_limit
                                         THEN 1 ELSE 0 END), 0)         AS over_limit
                FROM (%s%s) grouped"""
                .formatted(select(filter), havingSql(filter));
    }

    /**
     * The grouped select both statements are built from.
     * <p>
     * {@code LEFT JOIN table_area}, as everywhere else the area is joined: a party whose area row has
     * been deleted still owes what they owe. An inner join there is what once dropped such a customer
     * out of the receivables summary entirely while the ledger behind it kept every row of the debt.
     */
    private static String select(PartyBalanceFilter filter) {
        PartyLedgerSpec ledger = PartyLedgerSpec.of(filter.partyKind());
        PartyTableSpec party = PartyTableSpec.of(filter.partyKind());
        String limitColumn = filter.hasCreditLimit() ? "p.limit_num" : "0";
        String tierColumn = filter.hasCreditLimit() ? "p.price_id" : "0";
        // Only real columns may be grouped. A supplier selects the limit and the tier as a literal
        // 0, and MySQL reads a number in a GROUP BY as a column *position*: "GROUP BY ..., 0, 0"
        // was "Unknown column '0' in 'group statement'", and the supplier accounts screen failed
        // on every open - while every test here passed, because none of them read the GROUP BY.
        String groupedPartyColumns = filter.hasCreditLimit()
                ? ", " + limitColumn + ", " + tierColumn : "";
        return """
                SELECT m.%7$s                                        AS party_id,
                       p.%8$s                                        AS party_name,
                       COALESCE(p.tel, '')                           AS party_phone,
                       COALESCE(ta.id, 0)                            AS area_id,
                       COALESCE(ta.area_name, '')                    AS area_name,
                       %5$s                                          AS credit_limit,
                       %6$s                                          AS price_tier_id,
                       %1$s                                          AS balance,
                       ROUND(SUM(CASE WHEN m.account_date BETWEEN ? AND ?
                                      THEN GREATEST(m.purchase - m.discount, 0)
                                           + GREATEST(-m.paid, 0) ELSE 0 END), 2) AS period_debit,
                       ROUND(SUM(CASE WHEN m.account_date BETWEEN ? AND ?
                                      THEN GREATEST(m.paid, 0)
                                           + GREATEST(-(m.purchase - m.discount), 0) ELSE 0 END), 2) AS period_credit,
                       MAX(CASE WHEN m.account_date <= ? THEN m.account_date END) AS last_movement
                FROM %2$s m
                         JOIN %3$s p ON p.%4$s = m.%7$s
                         LEFT JOIN table_area ta ON ta.id = p.area_id
                WHERE (? IS NULL OR p.area_id = ?)
                  AND (? IS NULL OR %6$s = ?)
                  AND (? IS NULL OR p.%8$s LIKE ? ESCAPE '!' OR p.tel LIKE ? ESCAPE '!')
                GROUP BY m.%7$s, p.%8$s, p.tel, ta.id, ta.area_name%9$s"""
                .formatted(BALANCE, ledger.view(), party.table(), PartyTableSpec.KEY,
                        limitColumn, tierColumn, PartyLedgerSpec.PARTY, PartyTableSpec.NAME,
                        groupedPartyColumns);
    }

    /**
     * The conditions on the aggregates: the balance state, the range, the credit limit and idleness.
     * <p>
     * Assembled from the filter rather than always present, because a {@code HAVING} cannot use the
     * {@code ? IS NULL OR} trick on a column it also defines - MySQL resolves the alias, but the
     * optimiser then has to evaluate the group for every party whatever the filter says. The only
     * things concatenated are this class's own operators; every value is still bound.
     */
    private static String havingSql(PartyBalanceFilter filter) {
        StringBuilder having = new StringBuilder();
        String state = filter.state().havingSql("balance");
        if (!state.isEmpty()) {
            append(having, state);
        }
        if (filter.minBalance() != null) {
            append(having, "balance >= ?");
        }
        if (filter.maxBalance() != null) {
            append(having, "balance <= ?");
        }
        if (filter.overLimitOnly()) {
            append(having, "credit_limit > 0 AND balance > credit_limit");
        }
        if (filter.idleDays() != null) {
            append(having, "(last_movement IS NULL OR last_movement <= ?)");
        }
        return having.isEmpty() ? "" : "\nHAVING " + having;
    }

    private static void append(StringBuilder having, String condition) {
        if (!having.isEmpty()) {
            having.append(" AND ");
        }
        having.append(condition);
    }

    /** Areas a party of this kind is actually filed under, for the filter combo. */
    public static String areasSql(PartyKind kind) {
        PartyTableSpec party = PartyTableSpec.of(kind);
        return """
                SELECT DISTINCT ta.id, ta.area_name
                FROM table_area ta
                         JOIN %1$s p ON p.area_id = ta.id
                ORDER BY ta.area_name"""
                .formatted(party.table());
    }

    /** The wildcard-escaped pattern a text filter is bound as. */
    public static String pattern(String text) {
        String value = text == null ? "" : text.strip();
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
