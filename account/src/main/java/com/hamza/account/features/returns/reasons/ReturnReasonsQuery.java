package com.hamza.account.features.returns.reasons;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;

/**
 * Every statement the returns reasons report reads, pinned by {@code ReturnReasonsQueryTest}. Built from
 * {@link DocumentTableSpec}, so the two return tables' spellings are written in one place.
 *
 * <p><b>A return's value is its total less its own discount</b> - what it refunded or credited, the
 * figure {@code document_profit} signs against the sales. The reasons report used to sum the total alone,
 * so a return with a discount was reported at more than it was worth.</p>
 */
public final class ReturnReasonsQuery {

    /** At most this many items: the report names the items that came back most, not every one. */
    public static final int ITEM_LIMIT = 50;

    private ReturnReasonsQuery() {
    }

    /** One header's value. */
    static String value(String alias) {
        return alias + ".total - " + alias + ".discount";
    }

    /** Each reason's count and value. Parameters: from, to. */
    public static String reasonsSql(ReturnSide side) {
        DocumentTableSpec returns = side.returns();
        return "SELECT r.return_reason AS reason, COUNT(*) AS returns, COALESCE(SUM(" + value("r") + "), 0) AS value\n"
                + "FROM " + returns.table() + " r\n"
                + "WHERE r." + returns.dateColumn() + " BETWEEN ? AND ?\n"
                + "GROUP BY r.return_reason";
    }

    /**
     * The items that came back most, by the value of their lines. Quantity is in base units and a line's
     * amount is {@link ItemNetLines#lineAmount}, the same arithmetic every item report reads. Parameters:
     * from, to, the limit.
     */
    public static String itemsSql(ReturnSide side) {
        DocumentTableSpec returns = side.returns();
        String item = "l." + returns.lineItem();
        return "SELECT " + item + " AS item_id, MAX(i.nameItem) AS item_name,\n"
                + "       SUM(l.quantity * l.type_value) AS quantity,\n"
                + "       SUM(" + ItemNetLines.lineAmount(returns, "l") + ") AS value,\n"
                + "       COUNT(DISTINCT l." + DocumentTableSpec.LINE_DOCUMENT + ") AS returns\n"
                + "FROM " + returns.lineTable() + " l\n"
                + "         JOIN " + returns.table() + " r ON r." + returns.key() + " = l."
                + DocumentTableSpec.LINE_DOCUMENT + "\n"
                + "         LEFT JOIN items i ON i.id = " + item + "\n"
                + "WHERE r." + returns.dateColumn() + " BETWEEN ? AND ?\n"
                + "GROUP BY " + item + "\n"
                + "ORDER BY value DESC, item_id\n"
                + "LIMIT ?";
    }

    /**
     * The returns under one reason, or under none. Parameters: from, to, and the stored reason when
     * {@code withoutReason} is false.
     */
    public static String documentsSql(ReturnSide side, boolean withoutReason) {
        DocumentTableSpec returns = side.returns();
        return "SELECT r." + returns.key() + " AS number, r." + returns.dateColumn() + " AS return_date,\n"
                + "       COALESCE(p.name, '') AS party, COALESCE(r.source_invoice_number, 0) AS source_invoice,\n"
                + "       " + value("r") + " AS value, COALESCE(r.notes, '') AS notes\n"
                + "FROM " + returns.table() + " r\n"
                + "         LEFT JOIN " + returns.partyTable() + " p ON p.id = r." + returns.party() + "\n"
                + "WHERE r." + returns.dateColumn() + " BETWEEN ? AND ?\n"
                + (withoutReason ? "  AND (r.return_reason IS NULL OR r.return_reason = '')\n"
                : "  AND r.return_reason = ?\n")
                + "ORDER BY r." + returns.dateColumn() + ", r." + returns.key();
    }

    /** What the side's documents came to, net of their discounts. Parameters: from, to. */
    public static String documentsNetSql(ReturnSide side) {
        DocumentTableSpec documents = side.documents();
        return "SELECT COALESCE(SUM(" + value("d") + "), 0) AS net\n"
                + "FROM " + documents.table() + " d\n"
                + "WHERE d." + documents.dateColumn() + " BETWEEN ? AND ?";
    }
}
