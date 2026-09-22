package com.hamza.account.features.party.rfm;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import com.hamza.account.party.PartyTableSpec;

/**
 * The statements the recency, frequency and value table is read from.
 *
 * <p><b>Its figures are the party profile's, per customer.</b> An invoice counts on its own date and a
 * return on its own; the value is the headers' {@code total - discount}, sales less returns - the
 * expression {@code PartyProfileQuery.daysSql} sums - and the last purchase is the profile's "last
 * document ever", bounded by the period's end. {@code CustomerRfmDatabaseAcceptanceTest} holds every
 * row to the profile of the same customer over the same dates on MySQL: a table of every customer
 * that disagreed with the page of one would be the two-answers defect this repository keeps undoing.</p>
 *
 * <p><b>Who is scored:</b> every customer with a sale on or before the period's end, less the one cash
 * sales land on. A customer who bought before the period and not in it is kept, with no invoices and no
 * value - that row is what the table is opened to find. One who never bought has no recency and is not
 * a row.</p>
 *
 * <p><b>A score is the fifth of that population a figure falls in</b>, 5 the best: {@code RANK()} over
 * the figure, so equal figures share a score and the lowest tie takes the lowest. It is worked out in
 * the {@code scored} CTE, before the text condition, so a search never changes anybody's score. Each
 * side is grouped before it is joined, as everywhere else here.</p>
 */
public final class CustomerRfmQuery {

    /** {@link #scoredSql}: the period's end, the period twice, the period's end, the excluded party. */
    public static final int SCORED_PARAMETERS = 7;
    /** The text, twice: once to ask whether there is any, once as the pattern. */
    public static final int TEXT_PARAMETERS = 2;
    /** {@link #pageSql}: the scored rows, the text, the limit and the offset. */
    public static final int PAGE_PARAMETERS = SCORED_PARAMETERS + TEXT_PARAMETERS + 2;
    /** {@link #summarySql}: the scored rows and the text. */
    public static final int SUMMARY_PARAMETERS = SCORED_PARAMETERS + TEXT_PARAMETERS;

    /** The name of the customer left out, for the sentence that says who it is. */
    public static final String NAME_SQL = "SELECT %s FROM %s WHERE %s = ?"
            .formatted(PartyTableSpec.NAME, PartyTableSpec.CUSTOMER.table(), PartyTableSpec.KEY);

    static final String TEXT_CONDITION = "(? = '' OR s.name LIKE ? ESCAPE '!')";

    private CustomerRfmQuery() {
    }

    /** Every scored customer, as the CTEs the page and the summary both select from. */
    public static String scoredSql() {
        DocumentTableSpec sales = ItemNetLines.SALES.documents();
        DocumentTableSpec returns = ItemNetLines.SALES.returns();
        PartyTableSpec customers = PartyTableSpec.CUSTOMER;
        return """
                WITH last_sale AS (SELECT h.%1$s AS party_id, MAX(h.%2$s) AS last_day
                                   FROM %3$s h
                                   WHERE h.%2$s <= ?
                                   GROUP BY h.%1$s),
                     sold AS (SELECT h.%1$s AS party_id, COUNT(*) AS documents,
                                     SUM(h.total - h.discount) AS sold
                              FROM %3$s h
                              WHERE h.%2$s BETWEEN ? AND ?
                              GROUP BY h.%1$s),
                     returned AS (SELECT r.%4$s AS party_id, SUM(r.total - r.discount) AS returned
                                  FROM %5$s r
                                  WHERE r.%6$s BETWEEN ? AND ?
                                  GROUP BY r.%4$s),
                     activity AS (SELECT l.party_id,
                                         p.%7$s AS name,
                                         l.last_day,
                                         DATEDIFF(?, l.last_day) AS recency_days,
                                         COALESCE(s.documents, 0) AS documents,
                                         COALESCE(s.sold, 0) AS sold,
                                         COALESCE(t.returned, 0) AS returned,
                                         COALESCE(s.sold, 0) - COALESCE(t.returned, 0) AS net
                                  FROM last_sale l
                                           JOIN %8$s p ON p.%9$s = l.party_id
                                           LEFT JOIN sold s ON s.party_id = l.party_id
                                           LEFT JOIN returned t ON t.party_id = l.party_id
                                  WHERE l.party_id <> ?),
                     scored AS (SELECT a.party_id, a.name, a.last_day, a.recency_days, a.documents,
                                       a.sold, a.returned, a.net,
                                       1 + FLOOR(5 * (RANK() OVER (ORDER BY a.last_day) - 1) / COUNT(*) OVER ()) AS r_score,
                                       1 + FLOOR(5 * (RANK() OVER (ORDER BY a.documents) - 1) / COUNT(*) OVER ()) AS f_score,
                                       1 + FLOOR(5 * (RANK() OVER (ORDER BY a.net) - 1) / COUNT(*) OVER ()) AS m_score
                                FROM activity a)
                """.formatted(sales.party(), sales.dateColumn(), sales.table(),
                returns.party(), returns.table(), returns.dateColumn(),
                PartyTableSpec.NAME, customers.table(), PartyTableSpec.KEY);
    }

    /** One page of scored customers, in the order asked for, with one row more than fits. */
    public static String pageSql(CustomerRfmOrder order) {
        return scoredSql() + """
                SELECT s.party_id, s.name, s.last_day, s.recency_days, s.documents, s.sold, s.returned, s.net,
                       s.r_score, s.f_score, s.m_score
                FROM scored s
                WHERE %s
                ORDER BY %s
                LIMIT ? OFFSET ?""".formatted(TEXT_CONDITION, order.orderBy());
    }

    /** The figures for every row the text leaves - the page's own set, not the page. */
    public static String summarySql() {
        return scoredSql() + """
                SELECT COUNT(*) AS parties,
                       COALESCE(SUM(s.documents > 0), 0) AS buying,
                       COALESCE(SUM(s.documents), 0) AS documents,
                       COALESCE(SUM(s.net), 0) AS net
                FROM scored s
                WHERE %s""".formatted(TEXT_CONDITION);
    }
}
