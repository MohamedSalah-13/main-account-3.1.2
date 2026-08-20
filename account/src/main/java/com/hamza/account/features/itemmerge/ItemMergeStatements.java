package com.hamza.account.features.itemmerge;

/**
 * Every statement a merge runs, built in one place from {@link ItemReferenceRegistry}.
 * <p>
 * The table and column names come from the registry's constants and never from a
 * caller, so the concatenation below is not a way in. Keeping the text here rather
 * than inside the DAO methods is what lets {@code ItemMergeStatementsTest} pin it
 * character for character without a database - and a merge is exactly the kind of
 * change where a wrong statement still runs: swap the two parameters of a move and
 * every line of the target is handed to the item that is about to be deleted.
 */
public final class ItemMergeStatements {

    private ItemMergeStatements() {
    }

    // ---- the plain moves -----------------------------------------------------

    /** {@code (target, source)}. */
    public static String move(ItemReference reference) {
        return "UPDATE " + reference.table()
               + " SET " + reference.column() + " = ?"
               + " WHERE " + reference.column() + " = ?";
    }

    /** {@code (item)}. What the preview counts, and what the log records afterwards. */
    public static String count(ItemReference reference) {
        return "SELECT COUNT(*) FROM " + reference.table() + " WHERE " + reference.column() + " = ?";
    }

    // ---- the four documents, inside a closed period --------------------------
    //
    // A line has no date of its own; the header carries it. These are only ever used to
    // report the fact - the merge is allowed either way - so the bypass permission does
    // not come into it.

    /** {@code (item, lockedUntil)}. */
    public static final String COUNT_LOCKED_SALES = """
            SELECT COUNT(*) FROM sales s
                     JOIN total_sales h ON h.invoice_number = s.invoice_number
            WHERE s.num = ? AND h.invoice_date <= ?""";

    /** {@code (item, lockedUntil)}. */
    public static final String COUNT_LOCKED_SALES_RETURN = """
            SELECT COUNT(*) FROM sales_re r
                     JOIN total_sales_re h ON h.id = r.invoice_number
            WHERE r.item_id = ? AND h.invoice_date <= ?""";

    /** {@code (item, lockedUntil)}. */
    public static final String COUNT_LOCKED_PURCHASE = """
            SELECT COUNT(*) FROM purchase p
                     JOIN total_buy h ON h.invoice_number = p.invoice_number
            WHERE p.num = ? AND h.invoice_date <= ?""";

    /** {@code (item, lockedUntil)}. */
    public static final String COUNT_LOCKED_PURCHASE_RETURN = """
            SELECT COUNT(*) FROM purchase_re r
                     JOIN total_buy_re h ON h.id = r.invoice_number
            WHERE r.item_id = ? AND h.invoice_date <= ?""";

    // ---- finding the duplicates ----------------------------------------------

    // The letters folded before two names are compared, as code points rather than as
    // letters. They are data - nobody reads them and they answer no language - and
    // LocalizationArchitectureTest scans string literals under features/ for Arabic
    // because a literal there is nearly always a message that should have been a key.
    // Writing these as escapes says plainly that this one is not, rather than adding a
    // SQL builder to a list of files that owe translations.
    private static final String ALEF = "\u0627";
    private static final String ALEF_MADDA = "\u0622";
    private static final String ALEF_HAMZA_ABOVE = "\u0623";
    private static final String ALEF_HAMZA_BELOW = "\u0625";
    private static final String TAA_MARBUTA = "\u0629";
    private static final String HAA = "\u0647";
    private static final String ALEF_MAKSURA = "\u0649";
    private static final String YAA = "\u064A";

    /**
     * Arabic spelling folded down to what two people typing the same name have in
     * common: the three hamza forms of alef, the two forms of ya, and taa marbuta.
     * Nothing here is about meaning - it is about the fact that a name typed with one
     * form of alef and the same name typed with another are the same name, and sort as
     * two different ones.
     */
    private static String normalized(String column) {
        return "LOWER(TRIM(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(" + column
               + ", '" + ALEF_HAMZA_ABOVE + "', '" + ALEF + "')"
               + ", '" + ALEF_HAMZA_BELOW + "', '" + ALEF + "')"
               + ", '" + ALEF_MADDA + "', '" + ALEF + "')"
               + ", '" + TAA_MARBUTA + "', '" + HAA + "')"
               + ", '" + ALEF_MAKSURA + "', '" + YAA + "')))";
    }

    /**
     * The expression that decides which rows sit in one group. Alias {@code i} is
     * assumed, and used twice in {@link #candidates} - once inside the derived table
     * that finds the groups of more than one, once outside to join back to them.
     */
    static String groupKey(MergeGroupBy groupBy) {
        return switch (groupBy) {
            // Spaces go too: "شيبسي أطعم" and "شيبسي  اطعم" are one name.
            case NAME -> "REPLACE(" + normalized("i.nameItem") + ", ' ', '')";
            // Narrowed by the sub-group, or every item starting with a common word -
            // "زجاجة", "كيس" - lands in one heap that is no use to anybody.
            case FIRST_WORD -> "CONCAT(i.sub_num, ':', SUBSTRING_INDEX(" + normalized("i.nameItem") + ", ' ', 1))";
            case PRICE -> "CONCAT(i.sub_num, ':', i.sel_price1)";
        };
    }

    /**
     * Every item that shares its group with at least one other, with the two figures
     * that decide which of them is the survivor: how many document lines it has and
     * when it last moved.
     * <p>
     * Both come from {@code card_item_view}, so what the screen counts is exactly what
     * the item card lists. That view unions the four document families and joins each to
     * its header and its party, so this is not a cheap query - it is one query on a
     * screen somebody opens deliberately, and {@code limit} is what keeps a "same price"
     * grouping over a large catalogue from returning half of it.
     */
    public static String candidates(MergeGroupBy groupBy, int limit) {
        return """
                SELECT i.id,
                       i.nameItem,
                       i.barcode,
                       i.unit_id,
                       u.unit_name,
                       i.item_has_validity,
                       i.sel_price1,
                       i.first_balance,
                       %1$s AS group_key,
                       COALESCE(c.line_count, 0) AS line_count,
                       c.last_movement
                FROM items i
                         LEFT JOIN units u ON u.unit_id = i.unit_id
                         LEFT JOIN (SELECT item_num,
                                           COUNT(*)         AS line_count,
                                           MAX(invoice_date) AS last_movement
                                    FROM card_item_view
                                    GROUP BY item_num) c ON c.item_num = i.id
                         JOIN (SELECT %1$s AS k
                               FROM items i
                               GROUP BY k
                               HAVING COUNT(*) > 1) g ON g.k = %1$s
                ORDER BY group_key, line_count DESC, i.id
                LIMIT %2$d""".formatted(groupKey(groupBy), limit);
    }

    // ---- the item itself -----------------------------------------------------

    /**
     * {@code (item)}. The few columns a merge has to judge - not the whole model, which
     * carries an image blob and a JavaFX-bearing type this package has no use for.
     */
    public static final String SELECT_ITEM = """
            SELECT id, nameItem, barcode, unit_id, item_has_validity, first_balance
            FROM items
            WHERE id = ?""";

    /**
     * {@code (balance, target)}. The value is read in Java first: MySQL refuses a
     * subquery on {@code items} inside an {@code UPDATE} of {@code items}.
     */
    public static final String ADD_FIRST_BALANCE = "UPDATE items SET first_balance = first_balance + ? WHERE id = ?";

    // ---- stock count lines ---------------------------------------------------

    /** {@code (target, source)}. Both halves of the difference are added, so the difference survives. */
    public static final String SUM_STOCK_COUNT_LINES = """
            UPDATE stock_count_lines t
                     JOIN stock_count_lines s
                       ON s.count_id = t.count_id AND s.unit_id = t.unit_id
            SET t.system_qty  = t.system_qty  + s.system_qty,
                t.counted_qty = t.counted_qty + s.counted_qty
            WHERE t.item_id = ? AND s.item_id = ?""";

    /** {@code (target, source)}. The rows just added into the target's. */
    public static final String DELETE_SUMMED_STOCK_COUNT_LINES = """
            DELETE s FROM stock_count_lines s
                     JOIN stock_count_lines t
                       ON t.count_id = s.count_id AND t.unit_id = s.unit_id AND t.item_id = ?
            WHERE s.item_id = ?""";

    /** {@code (item, lockedUntil)}. */
    public static final String COUNT_LOCKED_STOCK_COUNT_LINES = """
            SELECT COUNT(*) FROM stock_count_lines l
                     JOIN stock_count c ON c.id = l.count_id
            WHERE l.item_id = ? AND c.count_date <= ?""";

    // ---- items_stock ---------------------------------------------------------

    /**
     * {@code (target, target, source)}. A row for every warehouse the source was in and
     * the target is not. There is one warehouse today, so this normally moves nothing -
     * it is here because the unique key says the plain move would fail if there were two.
     * <p>
     * The balances are seeded at zero on purpose: nothing reads
     * {@code items_stock.first_balance} - {@code quantity_items_table} reads
     * {@code items.first_balance} - and the opening balance is added there instead.
     */
    public static final String INSERT_MISSING_ITEMS_STOCK = """
            INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
            SELECT ?, s.stock_id, 0, 0
            FROM items_stock s
                     LEFT JOIN items_stock t ON t.item_id = ? AND t.stock_id = s.stock_id
            WHERE s.item_id = ? AND t.id IS NULL""";

    // ---- units ---------------------------------------------------------------

    /**
     * {@code (target, target, source, targetBaseUnit)}. Moved rather than copied: the
     * row takes its factor, its prices and its barcode with it, and a copy would
     * collide with the source's own row on the global {@code UNIQUE(items_barcode)}.
     * <p>
     * The target's base unit is excluded because it is {@code items.unit_id} and not a
     * row here at all - {@code ItemsDao} synthesizes it with a factor of 1, and a real
     * row for the same unit shows up twice on the item (V5 deleted exactly those).
     */
    public static final String MOVE_ABSENT_UNITS = """
            UPDATE items_units s
                     LEFT JOIN items_units t ON t.items_id = ? AND t.unit = s.unit
            SET s.items_id = ?
            WHERE s.items_id = ? AND t.id IS NULL AND s.unit <> ?""";

    // ---- barcodes ------------------------------------------------------------

    /**
     * {@code (target, source)}. {@code UPDATE IGNORE} because {@code UNIQUE(barcode)} is
     * global: a code the target already holds simply stays on the source, and goes with
     * it when the row is deleted.
     */
    public static final String MOVE_EXTRA_BARCODES = "UPDATE IGNORE item_barcodes SET item_id = ? WHERE item_id = ?";

    /**
     * {@code (target, source, source)}. The source's own {@code items.barcode} - the
     * code printed on the packet, which is the whole reason for merging - kept as an
     * extra barcode of the target before the row that holds it is deleted.
     * <p>
     * {@code items.barcode} is unique, so no other item can be holding it; a unit of
     * another item can, and the three barcode tables cannot see each other, so that one
     * is checked. {@code INSERT IGNORE} covers the case of the target already holding it.
     */
    public static final String KEEP_ITEM_BARCODE = """
            INSERT IGNORE INTO item_barcodes (item_id, barcode)
            SELECT ?, i.barcode
            FROM items i
            WHERE i.id = ?
              AND i.barcode <> ''
              AND NOT EXISTS (SELECT 1 FROM items_units u WHERE u.items_barcode = i.barcode AND u.items_id <> ?)""";

    /**
     * {@code (target, source, source)}. The codes on the unit rows that stayed behind -
     * the units the target already had - rescued for the same reason and on the same
     * terms. Run after {@link #MOVE_ABSENT_UNITS}, so what is left here is only what the
     * cascade is about to destroy.
     */
    public static final String KEEP_UNIT_BARCODES = """
            INSERT IGNORE INTO item_barcodes (item_id, barcode)
            SELECT ?, u.items_barcode
            FROM items_units u
            WHERE u.items_id = ?
              AND u.items_barcode IS NOT NULL
              AND u.items_barcode <> ''
              AND NOT EXISTS (SELECT 1 FROM items i WHERE i.barcode = u.items_barcode AND i.id <> ?)""";

    // ---- packages ------------------------------------------------------------

    /** {@code (target, source)} each. Two columns, both of them an item. */
    public static final String MOVE_PACKAGE_ITEM = "UPDATE items_package SET item_id = ? WHERE item_id = ?";
    public static final String MOVE_PACKAGE_PACKAGE = "UPDATE items_package SET package_id = ? WHERE package_id = ?";

    /**
     * No parameters. Nothing pairs the two columns uniquely, so repointing can leave the
     * same composition twice; the earliest row is the one kept, as V5 did for the units.
     */
    public static final String DELETE_DUPLICATE_PACKAGES = """
            DELETE p FROM items_package p
                     JOIN items_package keep
                       ON keep.item_id = p.item_id AND keep.package_id = p.package_id AND keep.id < p.id""";

    /** No parameters. Where the two merged items were a composition of each other, the row now points at itself. */
    public static final String DELETE_SELF_PACKAGES = "DELETE FROM items_package WHERE item_id = package_id";

    // ---- the log -------------------------------------------------------------

    public static final String INSERT_LOG = """
            INSERT INTO item_merge (target_item_id, target_item_name, source_item_id, source_item_name,
                                    source_barcode, source_first_balance, locked_period_lines, user_id, user_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    public static final String INSERT_LOG_LINE =
            "INSERT INTO item_merge_lines (merge_id, table_name, rows_moved) VALUES (?, ?, ?)";
}
