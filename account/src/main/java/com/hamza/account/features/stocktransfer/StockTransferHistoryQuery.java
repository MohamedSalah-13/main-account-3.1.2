package com.hamza.account.features.stocktransfer;

import java.sql.Date;

/**
 * Every statement the transfer history reads, built around <b>one</b> {@code WHERE}.
 * <p>
 * The page, its totals and the printed log are three statements and one set of transfers: they
 * share {@link #WHERE} and {@link #whereValues}, so the footer cannot count a different set from
 * the table, and the paper cannot print one - the rule {@code ItemsDao.catalogQuery} and
 * {@code TreasuryStatements}' history lists follow. Pinned character for character by
 * {@code StockTransferHistoryQueryTest}, parameter count included: a statement bound with the
 * wrong number of values fails, and one bound in the wrong order filters by the wrong thing.
 * <p>
 * Two brackets in the {@code WHERE} are load-bearing. {@code a OR b AND c} is {@code a OR (b AND c)},
 * so the warehouse pair and the text alternatives each sit inside their own - unbracketed, a match
 * on the note would let a transfer of every warehouse through.
 * <p>
 * The text matches the note, an item's name, or one of the three places a code lives - the item's
 * own barcode, {@code item_barcodes}, and a unit's barcode - exactly: a partial code matches half the
 * catalogue. Its wildcards are escaped ({@link #pattern}), so a {@code %} typed into the box is a
 * percent sign and not "everything".
 */
public final class StockTransferHistoryQuery {

    static final String WHERE = """
            WHERE t.transfer_date BETWEEN ? AND ?
              AND (? IS NULL OR t.stock_from = ? OR t.stock_to = ?)
              AND (? IS NULL
                   OR t.notes LIKE ? ESCAPE '!'
                   OR EXISTS (SELECT 1
                              FROM stock_transfer_list ml
                                       JOIN items mi ON mi.id = ml.item_id
                              WHERE ml.stock_transfer_id = t.id
                                AND (mi.nameItem LIKE ? ESCAPE '!'
                                     OR mi.barcode = ?
                                     OR mi.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
                                     OR mi.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?))))""";

    /**
     * One row per transfer, newest first. The line count is a subquery rather than a join and a
     * {@code GROUP BY}, so a transfer is one row whatever it carries - and one with no line, which
     * nothing in the application can make but a database edited by hand can hold, is listed rather
     * than silently dropped by an inner join.
     */
    public static final String PAGE = """
            SELECT t.id, t.transfer_date, t.stock_from, sf.stock_name AS name_from,
                   t.stock_to, st.stock_name AS name_to, t.notes, u.user_name,
                   (SELECT COUNT(*) FROM stock_transfer_list cl WHERE cl.stock_transfer_id = t.id) AS line_count
            FROM stock_transfer t
                     JOIN stocks sf ON sf.stock_id = t.stock_from
                     JOIN stocks st ON st.stock_id = t.stock_to
                     LEFT JOIN users u ON u.id = t.user_id
            %s
            ORDER BY t.transfer_date DESC, t.id DESC
            LIMIT ? OFFSET ?
            """.formatted(WHERE);

    /** How many transfers and how many lines - over the whole filtered set, never the page. */
    public static final String TOTALS = """
            SELECT COUNT(DISTINCT t.id) AS transfers, COUNT(l.id) AS line_count
            FROM stock_transfer t
                     LEFT JOIN stock_transfer_list l ON l.stock_transfer_id = t.id
            %s
            """.formatted(WHERE);

    /**
     * The printed log and its spreadsheet: every line of every transfer the filter matches, oldest
     * first, as it was entered. A transfer matched by one of its items is printed whole - the log
     * describes the transfers the list shows, not a second selection of lines.
     */
    public static final String LOG = """
            SELECT t.id, t.transfer_date, sf.stock_name AS name_from, st.stock_name AS name_to,
                   i.barcode, i.nameItem, un.unit_name, l.quantity, t.notes
            FROM stock_transfer t
                     JOIN stocks sf ON sf.stock_id = t.stock_from
                     JOIN stocks st ON st.stock_id = t.stock_to
                     JOIN stock_transfer_list l ON l.stock_transfer_id = t.id
                     JOIN items i ON i.id = l.item_id
                     LEFT JOIN units un ON un.unit_id = l.type
            %s
            ORDER BY t.transfer_date, t.id, l.id
            LIMIT ?
            """.formatted(WHERE);

    /** One transfer's header, read again by its id for its slip - the stored row, never the list's copy. */
    public static final String HEADER = """
            SELECT t.id, t.transfer_date, t.stock_from, sf.stock_name AS name_from,
                   t.stock_to, st.stock_name AS name_to, t.notes, u.user_name,
                   (SELECT COUNT(*) FROM stock_transfer_list cl WHERE cl.stock_transfer_id = t.id) AS line_count
            FROM stock_transfer t
                     JOIN stocks sf ON sf.stock_id = t.stock_from
                     JOIN stocks st ON st.stock_id = t.stock_to
                     LEFT JOIN users u ON u.id = t.user_id
            WHERE t.id = ?
            """;

    /** One transfer's lines in the order they were entered. */
    public static final String LINES = """
            SELECT l.item_id, i.barcode, i.nameItem, un.unit_name, l.quantity
            FROM stock_transfer_list l
                     JOIN items i ON i.id = l.item_id
                     LEFT JOIN units un ON un.unit_id = l.type
            WHERE l.stock_transfer_id = ?
            ORDER BY l.id
            """;

    private StockTransferHistoryQuery() {
    }

    /** Every value {@link #WHERE} binds, in the order it binds them. */
    public static Object[] whereValues(StockTransferHistoryFilter filter) {
        String text = filter.text();
        String like = text == null ? null : pattern(text);
        return new Object[]{Date.valueOf(filter.from()), Date.valueOf(filter.to()),
                filter.stockId(), filter.stockId(), filter.stockId(),
                text, like, like, text, text, text};
    }

    /** A contains-match with {@code !} as the escape, so the user's {@code %} and {@code _} are literal. */
    static String pattern(String text) {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
