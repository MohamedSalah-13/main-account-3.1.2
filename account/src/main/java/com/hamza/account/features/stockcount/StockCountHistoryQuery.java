package com.hamza.account.features.stockcount;

import java.sql.Date;

/**
 * Every statement the count history and the variance report read, built around <b>one</b>
 * {@code WHERE} and <b>one</b> definition of a difference.
 * <p>
 * The page, its totals and the variance share {@link #WHERE} and {@link #whereValues} - the
 * variance with the status forced to posted ({@link StockCountHistoryFilter#postedOnly}) - so the
 * report cannot describe other sheets than the history filtered to posted ones. Pinned, parameter
 * counts included, by {@code StockCountHistoryQueryTest}.
 * <p>
 * {@link #DIFFERENCE} is {@code adjustment_agg}'s own expression in {@code R__views.sql}, character
 * for character, and the test reads it out of that file: what the history calls a difference and
 * what the balance was moved by are one thing. It is left unrounded, as the view leaves it.
 */
public final class StockCountHistoryQuery {

    /** What one line moved a balance by, in base units - the view's own expression. */
    public static final String DIFFERENCE = "scl.counted_qty * scl.type_value - scl.system_qty";

    static final String WHERE = """
            WHERE c.count_date BETWEEN ? AND ?
              AND (? IS NULL OR c.stock_id = ?)
              AND (? IS NULL OR c.status = ?)""";

    /**
     * One row per sheet, newest first. Both counts are subqueries rather than a join and a
     * {@code GROUP BY}, so a sheet is one row however many lines it carries - and a draft with no
     * line yet is listed rather than dropped by an inner join.
     */
    public static final String PAGE = """
            SELECT c.id, c.count_date, c.stock_id, s.stock_name, c.status, c.notes, c.posted_at, u.user_name,
                   (SELECT COUNT(*) FROM stock_count_lines scl WHERE scl.count_id = c.id) AS line_count,
                   (SELECT COUNT(*) FROM stock_count_lines scl
                     WHERE scl.count_id = c.id AND %1$s <> 0) AS difference_count
            FROM stock_count c
                     JOIN stocks s ON s.stock_id = c.stock_id
                     LEFT JOIN users u ON u.id = c.user_id
            %2$s
            ORDER BY c.count_date DESC, c.id DESC
            LIMIT ? OFFSET ?
            """.formatted(DIFFERENCE, WHERE);

    /**
     * Sheets, lines and lines with a difference - over the whole filtered set, never the page. The
     * lines are joined, so the sheets are counted {@code DISTINCT}, and the join is {@code LEFT}, or
     * an empty draft would be on the page and missing from the total.
     */
    public static final String TOTALS = """
            SELECT COUNT(DISTINCT c.id) AS sheets, COUNT(scl.id) AS line_count,
                   COALESCE(SUM(%1$s <> 0), 0) AS difference_count
            FROM stock_count c
                     LEFT JOIN stock_count_lines scl ON scl.count_id = c.id
            %2$s
            """.formatted(DIFFERENCE, WHERE);

    /** One sheet's header, read again by its id for its paper - the stored row, never the list's copy. */
    public static final String HEADER = """
            SELECT c.id, c.count_date, c.stock_id, s.stock_name, c.status, c.notes, c.posted_at, u.user_name,
                   (SELECT COUNT(*) FROM stock_count_lines scl WHERE scl.count_id = c.id) AS line_count,
                   (SELECT COUNT(*) FROM stock_count_lines scl
                     WHERE scl.count_id = c.id AND %s <> 0) AS difference_count
            FROM stock_count c
                     JOIN stocks s ON s.stock_id = c.stock_id
                     LEFT JOIN users u ON u.id = c.user_id
            WHERE c.id = ?
            """.formatted(DIFFERENCE);

    /**
     * What the posted counts of the filter found, per item, largest shortage first.
     * <p>
     * <b>Summed per sheet and item before it is split into surplus and shortage.</b> An item on two
     * lines of one sheet - which the screen built until the defect {@code StockCountLines} describes
     * was fixed - would otherwise report a surplus and a shortage for one count of one item. Summed
     * first, each sheet contributes what it actually moved the item's balance by, which for those
     * old sheets is the wrong figure they really posted. The report says what happened to the book.
     */
    public static final String VARIANCE = """
            SELECT per.item_id, i.barcode, i.nameItem, un.unit_name,
                   COUNT(*) AS counts,
                   SUM(GREATEST(per.diff, 0)) AS surplus,
                   -SUM(LEAST(per.diff, 0)) AS shortage,
                   SUM(per.diff) AS net
            FROM (SELECT c.id AS count_id, scl.item_id, SUM(%1$s) AS diff
                  FROM stock_count c
                           JOIN stock_count_lines scl ON scl.count_id = c.id
                  %2$s
                  GROUP BY c.id, scl.item_id) per
                     JOIN items i ON i.id = per.item_id
                     LEFT JOIN units un ON un.unit_id = i.unit_id
            WHERE per.diff <> 0
            GROUP BY per.item_id, i.barcode, i.nameItem, un.unit_name
            ORDER BY net, i.nameItem
            LIMIT ?
            """.formatted(DIFFERENCE, WHERE);

    private StockCountHistoryQuery() {
    }

    /** Every value {@link #WHERE} binds, in the order it binds them. */
    public static Object[] whereValues(StockCountHistoryFilter filter) {
        String status = filter.status() == null ? null : filter.status().name();
        return new Object[]{Date.valueOf(filter.from()), Date.valueOf(filter.to()),
                filter.stockId(), filter.stockId(), status, status};
    }
}
