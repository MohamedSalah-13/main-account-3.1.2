package com.hamza.account.features.party.ageing;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.party.PartyTableSpec;

/**
 * What each party owes, split by how overdue it is.
 *
 * <p><b>The one thing this report must never do is become a fourth answer to "how much does
 * this party owe".</b> So it does not add its columns up and call the result a balance. It
 * reads the balance from the ledger view - the same expression {@code PartyBalanceQuery}
 * uses, which is the same arithmetic {@code account_customer_totals} uses - and then splits
 * that number, ages what it can, and puts the rest in a column of its own. The columns
 * therefore reconcile to the balance <b>by construction</b> rather than by my having
 * re-derived it correctly:
 *
 * <pre>current + 1-30 + 31-60 + 61-90 + over 90 + unallocated = balance</pre>
 *
 * <p><b>Only an invoice can be aged, and that is the whole reason allocation exists.</b> An
 * invoice has a date, a due date and a remaining amount ({@code total - discount - paid -
 * allocated}), so it can be placed in a band. The other three things in the ledger cannot:
 * an opening balance is settled by nothing in particular, a return is its own document and
 * is deliberately not netted off any invoice (see {@code OpenInvoiceQuery}), and a payment
 * left on account names no invoice at all. Guessing which invoice those belong to - oldest
 * first, say - is an invented rule of exactly the kind this codebase has twice had to undo,
 * and it would move real money between the columns a manager acts on.
 *
 * <p>So they are not guessed at. {@code unallocated} is <b>defined</b> as the balance less
 * what the open invoices account for, which makes it precisely "what this party owes that is
 * not on any open invoice". It is usually negative - payments taken on account - and a large
 * negative figure is the report telling you that allocating those payments would sharpen
 * every other column on the row.
 *
 * <p><b>Everything respects {@code asOf}.</b> The balance sums movements up to that day, so
 * the invoice side must ignore invoices written after it and allocations made after it, or
 * the two halves would answer different questions and the reconciliation would fail. That is
 * the property {@code PartyAgeingQueryTest} pins hardest.
 *
 * <p>Only identifiers the specifications own are concatenated; every user value is bound, and
 * the text search escapes its wildcards with {@code ESCAPE '!'}.
 */
public final class PartyAgeingQuery {

    private PartyAgeingQuery() {
    }

    /** Which document family is aged: a sale for a customer, a purchase for a supplier. */
    public static DocumentType invoiceType(PartyKind kind) {
        return DocumentType.of(kind, false);
    }

    /**
     * The balance as at a day, read the way every other screen reads it.
     * <p>
     * Character for character the expression in {@code PartyBalanceQuery}, and it must stay
     * that way: two statements of one rule is the shape of defect this package exists to
     * avoid. {@code PartyAgeingQueryTest} asserts the two agree.
     */
    static final String BALANCE =
            "ROUND(SUM(CASE WHEN m.account_date <= ? THEN m.purchase - m.discount - m.paid ELSE 0 END), 2)";

    /**
     * One page of the ageing list.
     * <p>
     * Parameters in order: {@code asOf} for the balance; then the aged sub-select's own -
     * {@code asOf} for its allocations, {@code asOf} for its invoices, and {@code asOf} for
     * each of the five bands; then the row filters - area, text ×3 - then the {@code HAVING}
     * conditions, and finally the limit and the offset.
     */
    public static String pageSql(PartyAgeingFilter filter) {
        return select(filter) + havingSql(filter)
                + "\nORDER BY balance DESC, p." + PartyTableSpec.NAME
                + "\nLIMIT ? OFFSET ?";
    }

    /**
     * What the whole filtered set comes to, per band.
     * <p>
     * Built from the same {@code select} the page is, for the reason {@code ItemsDao.catalogQuery}
     * gives: a footer and a table from two different {@code WHERE} clauses start describing two
     * different sets of parties, and nothing announces it.
     */
    public static String summarySql(PartyAgeingFilter filter) {
        StringBuilder sums = new StringBuilder();
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            sums.append("       COALESCE(SUM(aged.").append(column(bucket)).append("), 0) AS ")
                    .append(column(bucket)).append(",\n");
        }
        return "SELECT COUNT(*) AS parties,\n"
                + sums
                + "       COALESCE(SUM(aged.balance), 0) AS balance,\n"
                + "       COALESCE(SUM(aged.unallocated), 0) AS unallocated\n"
                + "FROM (" + select(filter) + havingSql(filter) + ") aged";
    }

    /** The column a band is selected as. Derived from the enum so the two cannot drift. */
    static String column(AgeingBucket bucket) {
        return "bucket_" + bucket.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String select(PartyAgeingFilter filter) {
        PartyTableSpec party = PartyTableSpec.of(filter.kind());
        PartyLedgerSpec ledger = PartyLedgerSpec.of(filter.kind());

        StringBuilder buckets = new StringBuilder();
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            buckets.append("       COALESCE(aged.").append(column(bucket)).append(", 0) AS ")
                    .append(column(bucket)).append(",\n");
        }

        return """
                SELECT p.%2$s                                        AS party_id,
                       p.%3$s                                        AS party_name,
                       p.tel                                         AS party_phone,
                       COALESCE(area.area_name, '')                  AS area_name,
                       p.payment_terms_days                          AS payment_terms_days,
                %6$s       %1$s AS balance,
                       ROUND(%1$s - (%7$s), 2) AS unallocated
                FROM %4$s p
                     LEFT JOIN table_area area ON area.id = p.area_id
                     JOIN %5$s m ON m.%8$s = p.%2$s
                     LEFT JOIN (%9$s) aged ON aged.party_id = p.%2$s
                WHERE 1 = 1%10$s
                GROUP BY p.%2$s, p.%3$s, p.tel, area.area_name, p.payment_terms_days%11$s
                """
                .formatted(BALANCE, PartyTableSpec.KEY, PartyTableSpec.NAME,
                        party.table(), ledger.view(), buckets,
                        agedTotalExpression(), PartyLedgerSpec.PARTY,
                        agedSql(filter.kind()), rowFilterSql(filter), agedGroupBy());
    }

    /** The five bands added together - what the open invoices account for. */
    private static String agedTotalExpression() {
        StringBuilder total = new StringBuilder();
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            if (total.length() > 0) {
                total.append(" + ");
            }
            total.append("COALESCE(aged.").append(column(bucket)).append(", 0)");
        }
        return total.toString();
    }

    /** Every aged column repeated in the GROUP BY, since they come from the joined derived table. */
    private static String agedGroupBy() {
        StringBuilder group = new StringBuilder();
        for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
            group.append(", aged.").append(column(bucket));
        }
        return group.toString();
    }

    /**
     * The open invoices of every party, summed into bands.
     * <p>
     * The band is decided in SQL from two dates and an integer, never from a label:
     * {@code DATEDIFF(asOf, invoice_date + payment_terms_days)}. The terms come from the
     * party's own row, so two customers with the same invoice date and different agreed terms
     * land in different columns - which is the entire point of {@code V56}.
     * <p>
     * {@code HAVING remaining > 0} rather than a {@code WHERE}: it compares against the
     * allocation aggregate. An invoice settled exactly is not open and must not occupy a band.
     */
    private static String agedSql(PartyKind kind) {
        DocumentTableSpec invoice = DocumentTableSpec.of(invoiceType(kind));
        PartyLedgerSpec ledger = PartyLedgerSpec.of(kind);
        PartyTableSpec party = PartyTableSpec.of(kind);

        StringBuilder bands = new StringBuilder();
        AgeingBucket[] order = AgeingBucket.inReadingOrder();
        for (int index = 0; index < order.length; index++) {
            AgeingBucket bucket = order[index];
            bands.append("              ROUND(SUM(CASE WHEN ").append(bandCondition(bucket))
                    .append(" THEN open.remaining ELSE 0 END), 2) AS ").append(column(bucket));
            bands.append(index == order.length - 1 ? "\n" : ",\n");
        }

        return """
                SELECT open.party_id,
                %7$s       FROM (SELECT d.%3$s AS party_id,
                                    DATEDIFF(?, DATE_ADD(d.%6$s, INTERVAL op.payment_terms_days DAY)) AS days_overdue,
                                    ROUND(d.total - d.discount - d.%4$s
                                          - COALESCE((SELECT SUM(a.paid - a.purchase)
                                                      FROM %5$s a
                                                      WHERE a.%8$s = d.%3$s
                                                        AND a.numberInv = d.%2$s
                                                        AND a.account_date <= ?), 0), 2) AS remaining
                             FROM %1$s d
                                  JOIN %9$s op ON op.%10$s = d.%3$s
                             WHERE d.%6$s <= ?
                             GROUP BY d.%2$s, d.%3$s, d.%6$s, d.total, d.discount, d.%4$s,
                                      op.payment_terms_days
                             HAVING remaining > 0) open
                       GROUP BY open.party_id"""
                .formatted(invoice.table(), invoice.key(), invoice.party(), invoice.paid(),
                        ledger.table(), invoice.dateColumn(), bands, PartyLedgerSpec.PARTY,
                        party.table(), PartyTableSpec.KEY);
    }

    /**
     * The condition that puts a debt in one band.
     * <p>
     * Written from the enum's own bounds so the SQL and {@link AgeingBucket#of(long)} cannot
     * disagree - one of them deciding the screen and the other the export would be a report
     * whose totals move depending on which button was pressed.
     */
    static String bandCondition(AgeingBucket bucket) {
        if (bucket == AgeingBucket.CURRENT) {
            return "open.days_overdue <= 0";
        }
        if (bucket == AgeingBucket.OVER_90) {
            return "open.days_overdue >= " + bucket.from();
        }
        return "open.days_overdue BETWEEN " + bucket.from() + " AND " + bucket.to();
    }

    /**
     * The filters that narrow which parties appear, as opposed to which debts are aged.
     * <p>
     * Note what is <b>not</b> here: nothing filters the invoices themselves. Hiding an invoice
     * from the aged sub-select while the balance still counted it would break the
     * reconciliation silently, which is the one thing this report cannot afford.
     */
    private static String rowFilterSql(PartyAgeingFilter filter) {
        StringBuilder where = new StringBuilder();
        if (filter.areaId() != null) {
            where.append("\n  AND p.area_id = ?");
        }
        if (filter.hasText()) {
            where.append("\n  AND (p.").append(PartyTableSpec.NAME)
                    .append(" LIKE ? ESCAPE '!' OR p.tel LIKE ? ESCAPE '!' OR p.")
                    .append(PartyTableSpec.KEY).append(" = ?)");
        }
        return where.toString();
    }

    /**
     * The conditions that compare against the aggregates, so they belong in a {@code HAVING}.
     * <p>
     * {@code overdueOnly} asks about the four overdue bands and not about the balance: a party
     * whose balance is zero because an old unpaid invoice is offset by a newer payment on
     * account still has an overdue invoice, and is exactly who this report is opened to find.
     */
    private static String havingSql(PartyAgeingFilter filter) {
        StringBuilder having = new StringBuilder();
        if (filter.overdueOnly()) {
            StringBuilder overdue = new StringBuilder();
            for (AgeingBucket bucket : AgeingBucket.inReadingOrder()) {
                if (bucket.isOverdue()) {
                    if (overdue.length() > 0) {
                        overdue.append(" + ");
                    }
                    overdue.append("COALESCE(aged.").append(column(bucket)).append(", 0)");
                }
            }
            having.append("\nHAVING ").append(overdue).append(" > 0");
        }
        if (filter.minimumBalance() != null) {
            having.append(having.length() == 0 ? "\nHAVING " : "\n   AND ")
                    .append("balance >= ?");
        }
        if (!filter.includeSettled()) {
            having.append(having.length() == 0 ? "\nHAVING " : "\n   AND ")
                    .append("(balance <> 0 OR ").append(agedTotalExpression()).append(" <> 0)");
        }
        return having.toString();
    }

    /** How many parties the filter matches - one statement, built from the same select. */
    public static String countSql(PartyAgeingFilter filter) {
        return "SELECT COUNT(*) FROM (" + select(filter) + havingSql(filter) + ") counted";
    }
}
