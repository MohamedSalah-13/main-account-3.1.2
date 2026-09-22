package com.hamza.account.features.party.profile;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import com.hamza.account.features.events.PartyKind;

/**
 * The three statements a profile is read from. Every table and column comes from
 * {@link ItemNetLines} and {@link DocumentTableSpec}, so a customer's profile reads the sales and
 * their returns and a supplier's the purchases and theirs, with no name written twice.
 *
 * <p><b>The items and the days are two readings of one set of documents, and must add up to each
 * other.</b> The items are read off the lines and the days off the headers; a header's
 * {@code total} is the sum of its lines after their own discounts, so the items' net less the
 * headers' own discounts is the days' net. {@link PartyProfileSummary#unexplained()} is whatever
 * does not, shown rather than hidden, and {@code PartyProfileDatabaseAcceptanceTest} holds it to
 * zero on MySQL.</p>
 *
 * <p>The cash is the header's cash column in both directions - what the party paid on its
 * documents less what was handed back on its returns - and the ledger's {@code purchase} column is
 * never read: a credit note is not a sale, which is how {@code DelegateActivityQuery} keeps it.</p>
 */
public final class PartyProfileQuery {

    /** {@link #itemsSql}: the period and the party, for the documents and again for the returns. */
    public static final int ITEMS_PARAMETERS = 6;
    /** {@link #daysSql}: the party and the period, for the documents and again for the returns. */
    public static final int DAYS_PARAMETERS = 6;
    public static final int LAST_DOCUMENT_PARAMETERS = 1;

    private PartyProfileQuery() {
    }

    static ItemNetLines linesFor(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? ItemNetLines.SALES : ItemNetLines.PURCHASES;
    }

    /** One row per item: base units and amounts net of returns, with the item's unit and group. */
    public static String itemsSql(PartyKind kind) {
        return """
                SELECT m.item_id AS item_id,
                       i.nameItem AS item_name,
                       COALESCE(u.unit_name, '') AS unit_name,
                       COALESCE(i.sub_num, 0) AS group_id,
                       COALESCE(g.name, '') AS group_name,
                       SUM(m.quantity) AS quantity,
                       SUM(m.amount) AS amount,
                       SUM(m.returned_amount) AS returned_amount,
                       SUM(m.documents) AS documents
                FROM (%s) m
                         JOIN items i ON i.id = m.item_id
                         LEFT JOIN units u ON u.unit_id = i.unit_id
                         LEFT JOIN sub_group g ON g.id = i.sub_num
                GROUP BY m.item_id, i.nameItem, u.unit_name, i.sub_num, g.name
                ORDER BY SUM(m.amount) - SUM(m.returned_amount) DESC, item_name"""
                .formatted(linesFor(kind).perItemSql(true));
    }

    /**
     * One row per day that holds a document or a return: counts, the headers' net, their cash and
     * their own discounts. Each side is grouped before the union, as the items are.
     */
    public static String daysSql(PartyKind kind) {
        ItemNetLines lines = linesFor(kind);
        DocumentTableSpec documents = lines.documents();
        DocumentTableSpec returns = lines.returns();
        return """
                SELECT m.day AS day,
                       SUM(m.documents) AS documents,
                       SUM(m.net) AS net,
                       SUM(m.cash) AS cash,
                       SUM(m.header_discount) AS header_discount,
                       SUM(m.returns) AS returns,
                       SUM(m.returned) AS returned,
                       SUM(m.refunded) AS refunded,
                       SUM(m.returns_header_discount) AS returns_header_discount
                FROM (SELECT h.%1$s AS day,
                             COUNT(*) AS documents,
                             SUM(h.total - h.discount) AS net,
                             SUM(h.%2$s) AS cash,
                             SUM(h.discount) AS header_discount,
                             0 AS returns,
                             0 AS returned,
                             0 AS refunded,
                             0 AS returns_header_discount
                      FROM %3$s h
                      WHERE h.%4$s = ? AND h.%1$s BETWEEN ? AND ?
                      GROUP BY h.%1$s
                      UNION ALL
                      SELECT r.%5$s, 0, 0, 0, 0,
                             COUNT(*),
                             SUM(r.total - r.discount),
                             SUM(r.%6$s),
                             SUM(r.discount)
                      FROM %7$s r
                      WHERE r.%8$s = ? AND r.%5$s BETWEEN ? AND ?
                      GROUP BY r.%5$s) m
                GROUP BY m.day
                ORDER BY m.day"""
                .formatted(documents.dateColumn(), documents.paid(), documents.table(), documents.party(),
                        returns.dateColumn(), returns.paid(), returns.table(), returns.party());
    }

    /** The party's last document ever, whatever the period - "when did he last buy". */
    public static String lastDocumentSql(PartyKind kind) {
        DocumentTableSpec documents = linesFor(kind).documents();
        return "SELECT MAX(h.%1$s) AS last_day FROM %2$s h WHERE h.%3$s = ?"
                .formatted(documents.dateColumn(), documents.table(), documents.party());
    }
}
