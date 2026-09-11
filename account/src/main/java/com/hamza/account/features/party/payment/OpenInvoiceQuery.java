package com.hamza.account.features.party.payment;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

/**
 * The invoices a party still owes something on, so a payment can be put against one.
 * <p>
 * <b>Why this is new rather than found.</b> {@code customers_accounts.numberInv} has existed
 * since {@code V1} and has an index of its own, the statement displays it, and the collection
 * screen passed a hard-coded zero into it for every payment ever taken. So the column said
 * "this payment settles no particular invoice" about all of them, and the question a shop asks
 * every day — which invoices is this customer still short on — had no answer anywhere. It is
 * also what an ageing report is made of: without allocation, "ninety days overdue" can only be
 * guessed from the age of a balance rather than measured from the age of what is unpaid.
 * <p>
 * <b>What counts as settled.</b> An invoice carries its own cash column, and payments allocated
 * to it add to that. What is left is {@code total - discount - paid - allocated}. Returns are
 * deliberately <em>not</em> netted off here: a return is its own document on the statement, not
 * a reduction of a particular invoice, and deciding which invoice a return belongs to would be
 * an invented rule — the kind this codebase has had to undo twice already, see
 * {@code DocumentLedgerEffect}. A customer who returns goods sees the credit on their statement
 * and a later payment is allocated accordingly.
 * <p>
 * The table, the key, the party column, the cash column and the date all come from
 * {@link DocumentTableSpec}, and the ledger's names from {@link PartyLedgerSpec}, so a renamed
 * column reaches this query instead of silently producing an empty picker.
 * {@code OpenInvoiceQueryTest} pins both statements and their parameter counts.
 */
public final class OpenInvoiceQuery {

    /**
     * How many invoices to offer. A picker, not a report: what gets asked about is the oldest
     * unsettled ones, and a party with more than two hundred open invoices needs the ageing
     * report rather than a dropdown.
     */
    public static final int LIMIT = 200;

    private OpenInvoiceQuery() {
    }

    /**
     * Which document family a payment from this party settles: a sale for a customer, a purchase
     * for a supplier. Their returns are not invoices and are not listed.
     */
    public static DocumentType invoiceType(PartyKind kind) {
        return DocumentType.of(kind, false);
    }

    /**
     * A party's unsettled invoices, oldest first.
     * <p>
     * What has been allocated is a correlated subquery rather than a join: joining the ledger
     * would produce one row per payment against each invoice and then need grouping by every
     * selected column. Here each invoice is one row by construction.
     * <p>
     * The filter sits in an outer query over a derived table rather than in a {@code HAVING}.
     * MySQL would accept {@code HAVING net - settled > 0} on a query with no {@code GROUP BY} as
     * an extension, and it would work — but it is not what {@code HAVING} means, and a reader
     * who knows the standard has to stop and check. This says what it does.
     * <p>
     * Parameters in textual order: the party (for the allocation subquery), the movement to
     * ignore, then the party again (for the invoice itself). The ignored movement is what lets
     * <b>editing</b> a payment see its invoice as it stood without that payment, rather than as
     * already settled by the very row being changed; pass {@code 0} for a new one, which matches
     * no movement.
     */
    public static String openInvoicesSql(PartyKind kind) {
        DocumentTableSpec invoice = DocumentTableSpec.of(invoiceType(kind));
        PartyLedgerSpec ledger = PartyLedgerSpec.of(kind);
        return """
                SELECT open.invoice_number, open.invoice_date, open.net, open.settled, open.notes
                FROM (SELECT d.%2$s                         AS invoice_number,
                             d.%6$s                         AS invoice_date,
                             ROUND(d.total - d.discount, 2) AS net,
                             ROUND(d.%4$s + %9$s, 2)        AS settled,
                             d.notes                        AS notes
                      FROM %1$s d
                      WHERE d.%3$s = ?) open
                WHERE open.net - open.settled > 0
                ORDER BY open.invoice_date, open.invoice_number
                LIMIT %10$d"""
                .formatted(invoice.table(), invoice.key(), invoice.party(), invoice.paid(),
                        ledger.table(), invoice.dateColumn(), PartyLedgerSpec.PARTY,
                        PartyLedgerSpec.KEY, allocatedSql(kind, "d." + invoice.key()), LIMIT);
    }

    /**
     * What one invoice still owes, for the check made while saving.
     * <p>
     * The picker is a convenience; this is the rule. A dialog holds its list for as long as it
     * is open, and a second till can settle the same invoice meanwhile — so the remaining amount
     * is read again inside the saving transaction rather than trusted from the row the user
     * clicked. Parameters in textual order: the party, the movement to ignore, the party again,
     * the invoice number.
     */
    public static String remainingOnInvoiceSql(PartyKind kind) {
        DocumentTableSpec invoice = DocumentTableSpec.of(invoiceType(kind));
        return """
                SELECT ROUND(d.total - d.discount - d.%4$s - %5$s, 2) AS remaining
                FROM %1$s d
                WHERE d.%3$s = ? AND d.%2$s = ?"""
                .formatted(invoice.table(), invoice.key(), invoice.party(), invoice.paid(),
                        allocatedSql(kind, "d." + invoice.key()));
    }

    /**
     * What the ledger has put against one invoice, written once so the picker and the save-time
     * check cannot come to disagree — the same reason
     * {@code PartyStatementQuery.rowFilterSql} is shared.
     * <p>
     * {@code paid - purchase} rather than {@code paid}: a credit note allocated to an invoice
     * reduces what is owed on it, and a debit note raises it. Both are movements on the same
     * ledger and both may name an invoice.
     */
    private static String allocatedSql(PartyKind kind, String invoiceColumn) {
        PartyLedgerSpec ledger = PartyLedgerSpec.of(kind);
        return """
                COALESCE((SELECT SUM(m.paid - m.purchase)
                                             FROM %1$s m
                                             WHERE m.%2$s = ?
                                               AND m.numberInv = %4$s
                                               AND m.%3$s <> ?), 0)"""
                .formatted(ledger.table(), PartyLedgerSpec.PARTY, PartyLedgerSpec.KEY, invoiceColumn);
    }
}
