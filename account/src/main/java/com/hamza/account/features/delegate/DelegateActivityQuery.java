package com.hamza.account.features.delegate;

/**
 * What each delegate sold, took back and collected in a period - the two things a commission can
 * be a percentage of, grouped in SQL. Pinned character for character by
 * {@code DelegateActivityQueryTest}, with each statement's parameter count.
 *
 * <p><b>It defines nothing of its own.</b> A document's net is {@code total - discount}, which is
 * what {@code OpenInvoiceQuery} and the account views read, and cash is the documents' own cash
 * columns ({@code paid_up}, {@code paid_from_treasury}) and {@code customers_accounts.paid} -
 * the three things {@code treasury_balance} unions as money through a till for a customer. So
 * the sales column adds up to the totals screen's figure for the same period, and
 * {@code DelegateActivityDatabaseAcceptanceTest} holds it to that on MySQL.
 */
public final class DelegateActivityQuery {

    /**
     * One row per delegate. Each side is grouped on its own before the join: joining the three
     * tables first and grouping after would multiply an invoice by every collection of its
     * delegate.
     *
     * <p><b>{@code collected} is cash, in both directions.</b> The cash part of his invoices and
     * the collections attributed to him, less what was refunded in cash on his customers'
     * returns - a pound handed back over the counter did not stay collected. A credit note is
     * not here at all: it is {@code customers_accounts.purchase}, which is not cash and which
     * this query never reads.
     *
     * <p>A return counts in the period of the <b>return</b>, whatever month its invoice was in:
     * a month already paid for is not reopened (decision 2 of the plan).
     *
     * <p>Who is listed: every active delegate, so that one with a target and no sales is on the
     * report rather than absent from it - the inner join that hid exactly him is one of the
     * defects of the view this replaces - and anybody else the period's figures name, such as a
     * delegate since stopped.
     */
    public static final String ACTIVITY_SQL = """
            SELECT e.id,
                   e.column_name,
                   e.is_active,
                   COALESCE(s.net, 0)                                          AS sales,
                   COALESCE(r.net, 0)                                          AS sales_returns,
                   COALESCE(s.cash, 0) - COALESCE(r.cash, 0) + COALESCE(c.paid, 0) AS collected
            FROM employees e
                     JOIN jobs j ON j.id = e.job
                     LEFT JOIN (SELECT delegate_id, SUM(total - discount) AS net, SUM(paid_up) AS cash
                                FROM total_sales
                                WHERE invoice_date BETWEEN ? AND ?
                                GROUP BY delegate_id) s ON s.delegate_id = e.id
                     LEFT JOIN (SELECT delegate_id, SUM(total - discount) AS net, SUM(paid_from_treasury) AS cash
                                FROM total_sales_re
                                WHERE invoice_date BETWEEN ? AND ?
                                GROUP BY delegate_id) r ON r.delegate_id = e.id
                     LEFT JOIN (SELECT delegate_id, SUM(paid) AS paid
                                FROM customers_accounts
                                WHERE account_date BETWEEN ? AND ?
                                  AND delegate_id IS NOT NULL
                                GROUP BY delegate_id) c ON c.delegate_id = e.id
            WHERE (j.is_delegate = 1 AND e.is_active = 1)
               OR s.delegate_id IS NOT NULL
               OR r.delegate_id IS NOT NULL
               OR c.delegate_id IS NOT NULL
            ORDER BY e.column_name""";

    /**
     * Collections that name no delegate: everything entered before V71, and a payment on account
     * from a customer with no default delegate. Reported as a figure of its own rather than
     * hidden, so the collected column plus this is what the tills took from customers' accounts.
     */
    public static final String UNATTRIBUTED_COLLECTIONS_SQL = """
            SELECT COALESCE(SUM(paid), 0)
            FROM customers_accounts
            WHERE account_date BETWEEN ? AND ?
              AND delegate_id IS NULL""";

    /**
     * Writes the delegate of one new collection - <b>at entry, once, never derived later</b>. A
     * delegate read off the customer at report time would let moving a customer between delegates
     * rewrite both delegates' history.
     *
     * <p>The rule is decision 3 of the plan: a collection allocated to an invoice is that
     * invoice's delegate's; one left on account is the customer's default delegate's on the day.
     * {@code default_delegate_id} carries no foreign key and may hold 0 or an employee since
     * deleted, so it is read through {@code employees} - the column written here does have a key.
     *
     * <p>Only a row that moved cash: a debit or a credit note is nobody's collection. And only a
     * row not yet attributed, so running it again changes nothing.
     */
    public static final String ATTRIBUTE_COLLECTION_SQL = """
            UPDATE customers_accounts ca
            SET ca.delegate_id = COALESCE(
                    (SELECT ts.delegate_id
                     FROM total_sales ts
                     WHERE ca.numberInv > 0
                       AND ts.invoice_number = ca.numberInv),
                    (SELECT e.id
                     FROM custom cu
                              JOIN employees e ON e.id = cu.default_delegate_id
                     WHERE cu.id = ca.account_code))
            WHERE ca.account_num = ?
              AND ca.paid <> 0
              AND ca.delegate_id IS NULL""";

    private DelegateActivityQuery() {
    }
}
