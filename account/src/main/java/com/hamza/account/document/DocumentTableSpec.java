package com.hamza.account.document;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.SqlStatements;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Where one kind of document lives, and what its columns are called.
 * <p>
 * The four families answer the same questions with different words - the key is
 * {@code invoice_number} on the two invoices and {@code id} on the two returns, the
 * party is {@code sup_code} or {@code sup_id}, what was settled in cash is
 * {@code paid_up}, {@code paid_from_treasury} or {@code paid_to_treasury}, and the item
 * on a line is {@code num} or {@code item_id}. None of those differences means anything;
 * they are what four tables written at four different times look like.
 * <p>
 * Collecting them here is what lets one statement serve all four. Every DAO used to
 * build its own {@code SELECT}, {@code DELETE ... IN}, by-party and by-year query out of
 * its own constants: the same six statements, written four times, differing only in the
 * words above. {@code DocumentDaoStatementsTest} pins the result of each, so this file
 * has to produce them character for character.
 * <p>
 * Deliberately separate from {@link DocumentType}, which is what a document <em>means</em>.
 * This is where its rows happen to sit, and it is the part that a single {@code documents}
 * table would eventually delete.
 *
 * @param type          which document this describes
 * @param table         the table written to
 * @param view          the view read from - it joins the party, stock and treasury names
 * @param key           the primary key: {@code invoice_number} or {@code id}
 * @param party         the customer or supplier column
 * @param paid          what the document settled in cash, under its three names
 * @param lineTable     where its lines are written
 * @param lineView      where its lines are read from
 * @param lineItem      the item column on a line: {@code num} or {@code item_id}
 * @param insertColumns the header insert, in the order the DAO's {@code getData} fills
 * @param updateColumns the header update's SET clause; the key is the WHERE and is not here
 * @param lineColumns   the line insert, in the order the DAO's line data fills
 */
public record DocumentTableSpec(
        DocumentType type,
        String table,
        String view,
        String key,
        String party,
        String paid,
        String lineTable,
        String lineView,
        String lineItem,
        List<String> insertColumns,
        List<String> updateColumns,
        List<String> lineColumns) {

    /** A printed page nobody reads past is not a report; a runaway group-by is a hang. */
    public static final int REPORT_ROW_LIMIT = 2000;

    /** As in {@code LockedDocument}: these are concatenated into SQL, so they are checked. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * The column a line points at its document with, and a line's own key. Unlike
     * everything else here, both are the same in all four line tables.
     */
    public static final String LINE_DOCUMENT = "invoice_number";
    public static final String LINE_KEY = "id";

    public static final DocumentTableSpec SALES = new DocumentTableSpec(
            DocumentType.SALES,
            "total_sales", "total_sales_names_table", "invoice_number", "sup_code", "paid_up",
            "sales", "sales_names_table", "num",
            List.of("sup_code", "invoice_type", "invoice_date", "total", "discount", "paid_up",
                    "stock_id", "delegate_id", "treasury_id", "notes", "invoice_number", "user_id"),
            List.of("sup_code", "invoice_type", "invoice_date", "total", "discount", "paid_up",
                    "stock_id", "delegate_id", "treasury_id", "notes"),
            List.of("invoice_number", "num", "type", "quantity", "price", "buy_price", "total_sel_price",
                    "total_buy_price", "total_profit", "discount", "type_value", "expiration_date"));

    public static final DocumentTableSpec PURCHASE = new DocumentTableSpec(
            DocumentType.PURCHASE,
            "total_buy", "total_purchase_names_table", "invoice_number", "sup_code", "paid_up",
            "purchase", "purchase_names_table", "num",
            // The user and the key are the other way round from the sale, for no reason
            // anybody recorded. Kept, because changing it changes nothing and risks something.
            List.of("sup_code", "invoice_type", "invoice_date", "total", "discount", "paid_up",
                    "stock_id", "treasury_id", "notes", "user_id", "invoice_number"),
            List.of("sup_code", "invoice_type", "invoice_date", "total", "discount", "paid_up",
                    "stock_id", "treasury_id", "notes"),
            List.of("invoice_number", "num", "type", "quantity", "price", "discount",
                    "type_value", "expiration_date"));

    public static final DocumentTableSpec SALES_RETURN = new DocumentTableSpec(
            DocumentType.SALES_RETURN,
            "total_sales_re", "total_sales_return_names_table", "id", "sup_id", "paid_from_treasury",
            "sales_re", "sales_return_names_table", "item_id",
            // And here the date comes before the type, and the key sits between the
            // treasury and the notes. Three orders for one document.
            List.of("sup_id", "invoice_date", "invoice_type", "total", "discount", "paid_from_treasury",
                    "stock_id", "delegate_id", "treasury_id", "id", "notes", "user_id"),
            List.of("sup_id", "invoice_date", "invoice_type", "total", "discount", "paid_from_treasury",
                    "stock_id", "delegate_id", "treasury_id", "notes"),
            // source_line_id last, as the two return families both carry it and neither
            // invoice family has it - see V16__return_source.sql.
            List.of("invoice_number", "item_id", "type", "quantity", "price", "buy_price", "total_sel_price",
                    "total_buy_price", "total_profit", "discount", "type_value", "expiration_date",
                    "source_line_id"));

    public static final DocumentTableSpec PURCHASE_RETURN = new DocumentTableSpec(
            DocumentType.PURCHASE_RETURN,
            "total_buy_re", "total_purchase_return_names_table", "id", "sup_id", "paid_to_treasury",
            "purchase_re", "purchase_return_names_table", "item_id",
            List.of("sup_id", "invoice_date", "invoice_type", "total", "discount", "paid_to_treasury",
                    "stock_id", "treasury_id", "id", "notes", "user_id"),
            List.of("sup_id", "invoice_date", "invoice_type", "total", "discount", "paid_to_treasury",
                    "stock_id", "treasury_id", "notes"),
            List.of("invoice_number", "item_id", "type", "quantity", "price", "discount",
                    "type_value", "expiration_date", "source_line_id"));

    public DocumentTableSpec {
        for (String identifier : new String[]{table, view, key, party, paid, lineTable, lineView, lineItem}) {
            requireIdentifier(identifier);
        }
        insertColumns = List.copyOf(insertColumns);
        updateColumns = List.copyOf(updateColumns);
        lineColumns = List.copyOf(lineColumns);
        insertColumns.forEach(DocumentTableSpec::requireIdentifier);
        updateColumns.forEach(DocumentTableSpec::requireIdentifier);
        lineColumns.forEach(DocumentTableSpec::requireIdentifier);
    }

    private static void requireIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Not an identifier: " + identifier);
        }
    }

    public static DocumentTableSpec of(DocumentType type) {
        return switch (type) {
            case SALES -> SALES;
            case SALES_RETURN -> SALES_RETURN;
            case PURCHASE -> PURCHASE;
            case PURCHASE_RETURN -> PURCHASE_RETURN;
        };
    }

    /** The business date, which is the one a closed period is about. */
    public String dateColumn() {
        return type.periodLock().dateColumn();
    }

    // ---- the header ----------------------------------------------------------------

    public String selectAllSql() {
        return SqlStatements.selectStatement(view);
    }

    public String selectByIdSql() {
        return SqlStatements.selectStatementByColumnWhere(view, key);
    }

    public String selectBetweenDatesSql() {
        return SqlStatements.selectStatement(view) + " WHERE " + dateColumn() + " BETWEEN ? AND ?";
    }

    public String selectByPartySql() {
        return SqlStatements.selectStatement(view) + " WHERE " + party + " = ?";
    }

    public String selectByYearSql() {
        return SqlStatements.selectStatement(view) + " WHERE YEAR(" + dateColumn() + ") = ?";
    }

    /**
     * Whether this family's view carries a delegate name at all - only the two sales-side
     * views join {@code employees}; a purchase has no delegate to search by.
     */
    public boolean hasDelegate() {
        return type == DocumentType.SALES || type == DocumentType.SALES_RETURN;
    }

    /** The two sales families carry a profit; a purchase has no revenue to earn one on. */
    public boolean hasProfit() {
        return hasDelegate();
    }

    /**
     * Whether a document of this family can also be settled later through a party payment.
     * Only the two invoice families: a return is refunded rather than paid off over time,
     * and neither return view carries {@code OtherPaid}.
     */
    public boolean hasOtherPaid() {
        return type == DocumentType.SALES || type == DocumentType.PURCHASE;
    }

    /** {@code custom} or {@code suppliers} - the table {@link #party} points at. */
    public String partyTable() {
        return type.partyKind() == PartyKind.CUSTOMER ? "custom" : "suppliers";
    }

    /** Where a later payment against one of these documents is recorded. */
    private String accountsTable() {
        return type.partyKind() == PartyKind.CUSTOMER ? "customers_accounts" : "suppliers_accounts";
    }

    // ---- searching -----------------------------------------------------------------

    /**
     * One page of a search, and the reason it is not simply {@code SELECT * FROM view}.
     * <p>
     * The {@code *_names_table} views {@code LEFT JOIN document_profit}, which is a
     * {@code UNION ALL} of two {@code GROUP BY} aggregations over the whole {@code sales}
     * and {@code sales_re} tables. MySQL cannot push a date - or any other - predicate
     * through that union, so it materializes the entire thing on <b>every</b> search.
     * Measured on 101,000 invoices and 506,000 lines: one month of data took
     * <b>11.9 seconds</b>, and adding {@code LIMIT 50} made it 12.9 - paging the view is
     * not an optimisation, because the whole cost is paid before the limit is reached.
     * <p>
     * So the page is taken first, from the base table with only the dimension joins a
     * filter can name, and everything per-document is resolved afterwards for the rows
     * that survived. The same measurement on this shape: <b>0.08 seconds</b> for the first
     * page with no filter at all, and 0.62 for page one thousand.
     * <p>
     * The cost is a correlated subquery on purpose. Written as
     * {@code LEFT JOIN (SELECT ... GROUP BY invoice_number)} it repeats the mistake
     * {@code document_profit} makes - measured at 1.4 seconds - because a derived table
     * with {@code GROUP BY} is materialized whole. Correlated, it runs once per row shown.
     * <p>
     * The profit is the expression {@code document_profit} defines: net revenue less the
     * recorded cost of the lines. For a return it is deliberately the magnitude, which is
     * what {@code total_sales_return_names_table} already produced by negating twice.
     */
    public String searchPageSql(TotalsSearchCriteria criteria, List<Object> params) {
        StringBuilder sql = new StringBuilder("SELECT p.*, st.stock_name, tr.t_name, us.user_name");
        if (hasProfit()) {
            String cost = lineCostSubquery("p");
            String net = "(p.total - p.discount)";
            sql.append(", ").append(cost).append(" AS total_buy_price")
                    .append(", ROUND(").append(net).append(" - ").append(cost).append(", 2) AS total_profit")
                    .append(", ROUND((").append(net).append(" - ").append(cost).append(") * 100")
                    .append(" / NULLIF(").append(net).append(", 0), 2) AS profit_percent");
        }
        if (hasOtherPaid()) {
            sql.append(", COALESCE((SELECT SUM(ac.paid) FROM ").append(accountsTable())
                    .append(" ac WHERE ac.numberInv = p.").append(key).append("), 0) AS OtherPaid");
        }
        sql.append(" FROM (SELECT d.*, pa.name");
        if (hasDelegate()) sql.append(", em.column_name");
        sql.append(fromAndWhere(criteria, params))
                .append(" ORDER BY d.").append(dateColumn()).append(" DESC, d.").append(key).append(" DESC")
                .append(" LIMIT ? OFFSET ?) p")
                .append(" JOIN stocks st ON st.stock_id = p.stock_id")
                .append(" JOIN treasury tr ON tr.id = p.treasury_id")
                .append(" JOIN users us ON us.id = p.user_id");
        return sql.toString();
    }

    /** How many rows the same conditions match, for the pager and the result count. */
    public String searchCountSql(TotalsSearchCriteria criteria, List<Object> params) {
        return "SELECT COUNT(*)" + fromAndWhere(criteria, params);
    }

    /**
     * The figures under the table, summed over the <b>whole</b> result rather than the page.
     * Summing the loaded rows was right while a search loaded all of them; with a page it
     * would report the rows on screen as if they were the search.
     */
    public String searchSummarySql(TotalsSearchCriteria criteria, List<Object> params) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS row_count")
                .append(", COALESCE(SUM(d.total), 0) AS sum_total")
                .append(", COALESCE(SUM(d.discount), 0) AS sum_discount")
                .append(", COALESCE(SUM(d.").append(paid).append("), 0) AS sum_paid");
        if (hasProfit()) {
            sql.append(", COALESCE(SUM(ROUND((d.total - d.discount) - ")
                    .append(lineCostSubquery("d")).append(", 2)), 0) AS sum_profit");
        } else {
            sql.append(", 0 AS sum_profit");
        }
        return sql.append(fromAndWhere(criteria, params)).toString();
    }

    // ---- reports ---------------------------------------------------------------------

    /**
     * The four summaries the totals screen prints, each over exactly the documents the
     * search matched - they take the same {@code criteria} and share {@link #fromAndWhere},
     * so a report can never quietly describe a wider set than the list it was printed from.
     * <p>
     * The profit column is present only for the two sales families, for the reason
     * {@link #hasProfit()} gives, and the caller is told which by asking that.
     */
    public enum Report {
        /** One row per customer or supplier, heaviest first. */
        BY_PARTY,
        /** One row per day. */
        BY_DAY,
        /** One row per month. */
        BY_MONTH,
        /** One row per delegate - sales families only, a purchase has none. */
        BY_DELEGATE,
        /** One row per item, by quantity sold. Reads the lines, so it has no cash columns. */
        BY_ITEM
    }

    /** Whether this family can answer that report at all. */
    public boolean supports(Report report) {
        return switch (report) {
            case BY_DELEGATE -> hasDelegate();
            case BY_PARTY, BY_DAY, BY_MONTH, BY_ITEM -> true;
        };
    }

    /**
     * A grouped summary over the search's own conditions.
     *
     * <p>{@link Report#BY_ITEM} is the one that reads the line table, and it deliberately
     * carries no profit: an invoice-level discount has no owner among the lines, and
     * splitting it needs an allocation rule nobody has agreed - the same reason
     * {@code card_item_view_details} stays gross of it. It reports quantity and revenue,
     * which are the questions the lines can actually answer.</p>
     */
    public String reportSql(Report report, TotalsSearchCriteria criteria, List<Object> params) {
        if (!supports(report)) {
            throw new IllegalArgumentException(type + " has no " + report + " report");
        }
        return switch (report) {
            case BY_PARTY -> grouped("pa.name", "label", criteria, params, "sum_total DESC");
            case BY_DAY -> grouped("d." + dateColumn(), "label", criteria, params, "label DESC");
            case BY_MONTH -> grouped("DATE_FORMAT(d." + dateColumn() + ", '%Y-%m')", "label",
                    criteria, params, "label DESC");
            case BY_DELEGATE -> grouped("em.column_name", "label", criteria, params, "sum_total DESC");
            case BY_ITEM -> itemReport(criteria, params);
        };
    }

    private String grouped(String expression, String alias, TotalsSearchCriteria criteria,
                           List<Object> params, String order) {
        StringBuilder sql = new StringBuilder("SELECT ").append(expression).append(" AS ").append(alias)
                .append(", COUNT(*) AS row_count")
                .append(", COALESCE(SUM(d.total), 0) AS sum_total")
                .append(", COALESCE(SUM(d.discount), 0) AS sum_discount")
                .append(", COALESCE(SUM(d.").append(paid).append("), 0) AS sum_paid");
        if (hasProfit()) {
            sql.append(", COALESCE(SUM(ROUND((d.total - d.discount) - ")
                    .append(lineCostSubquery("d")).append(", 2)), 0) AS sum_profit");
        } else {
            sql.append(", 0 AS sum_profit");
        }
        return sql.append(fromAndWhere(criteria, params, ""))
                .append(" GROUP BY ").append(expression)
                .append(" ORDER BY ").append(order)
                .append(" LIMIT ").append(REPORT_ROW_LIMIT).toString();
    }

    /**
     * Sold quantity and revenue per item. The lines are joined to the documents the search
     * matched, so every condition still applies - a report of "what this customer buys" is
     * the same filter, read one level down.
     */
    private String itemReport(TotalsSearchCriteria criteria, List<Object> params) {
        String joins = " JOIN " + lineTable + " ln ON ln." + LINE_DOCUMENT + " = d." + key
                + " JOIN items it ON it.id = ln." + lineItem;
        return "SELECT it.nameItem AS label"
                + ", COUNT(DISTINCT d." + key + ") AS row_count"
                + ", COALESCE(SUM(ln.quantity * ln.type_value), 0) AS sum_quantity"
                // quantity * price, not total_sel_price: only the two sales line tables
                // carry that column, and on all 6,348 real sales lines the two are equal
                // to the cent. Gross of the line's own discount, which has its own column.
                + ", COALESCE(SUM(ln.quantity * ln.price), 0) AS sum_total"
                + ", COALESCE(SUM(ln.discount), 0) AS sum_discount"
                + fromAndWhere(criteria, params, joins)
                + " GROUP BY it.id, it.nameItem"
                + " ORDER BY sum_quantity DESC"
                + " LIMIT " + REPORT_ROW_LIMIT;
    }

    /** The recorded cost of one document's lines, as {@code document_profit} sums it. */
    private String lineCostSubquery(String documentAlias) {
        return "COALESCE((SELECT SUM(ln.total_buy_price) FROM " + lineTable
                + " ln WHERE ln." + LINE_DOCUMENT + " = " + documentAlias + "." + key + "), 0)";
    }

    /**
     * The {@code FROM} and {@code WHERE} all three statements share, so the page, its count
     * and its summary can never start describing different sets of rows - the rule
     * {@code ItemsDao.catalogQuery} already follows.
     * <p>
     * Both dates are optional, and a search with neither is the whole history of that
     * document family. That is the point of it: a customer's first invoice is findable
     * without already knowing when they started.
     */
    private String fromAndWhere(TotalsSearchCriteria criteria, List<Object> params) {
        return fromAndWhere(criteria, params, "");
    }

    /**
     * @param extraJoins joins a particular statement needs, spliced in <b>before</b> the
     *                   {@code WHERE} - a report over the lines adds its own, and appending
     *                   them afterwards would not be SQL at all
     */
    private String fromAndWhere(TotalsSearchCriteria criteria, List<Object> params,
                                String extraJoins) {
        StringBuilder sql = new StringBuilder(" FROM ").append(table).append(" d")
                .append(" JOIN ").append(partyTable()).append(" pa ON pa.id = d.").append(party);
        if (hasDelegate()) sql.append(" JOIN employees em ON em.id = d.delegate_id");
        sql.append(extraJoins);
        sql.append(" WHERE 1 = 1");

        if (criteria.dateFrom() != null) {
            sql.append(" AND d.").append(dateColumn()).append(" >= ?");
            params.add(criteria.dateFrom().toString());
        }
        if (criteria.dateTo() != null) {
            sql.append(" AND d.").append(dateColumn()).append(" <= ?");
            params.add(criteria.dateTo().toString());
        }
        if (criteria.invoiceNumber() != null) {
            sql.append(" AND d.").append(key).append(" = ?");
            params.add(criteria.invoiceNumber());
        }
        if (hasText(criteria.partyName())) {
            sql.append(" AND pa.name = ?");
            params.add(criteria.partyName());
        }
        if (hasDelegate() && hasText(criteria.delegateName())) {
            sql.append(" AND em.column_name = ?");
            params.add(criteria.delegateName());
        }
        if (criteria.invoiceType() != null) {
            sql.append(" AND d.invoice_type = ?");
            params.add(criteria.invoiceType().getId());
        }
        if (hasText(criteria.enteredByUsername())) {
            sql.append(" AND d.user_id = (SELECT id FROM users WHERE user_name = ?)");
            params.add(criteria.enteredByUsername());
        }
        if (criteria.minTotal() != null) {
            sql.append(" AND d.total >= ?");
            params.add(criteria.minTotal());
        }
        if (criteria.maxTotal() != null) {
            sql.append(" AND d.total <= ?");
            params.add(criteria.maxTotal());
        }
        if (hasText(criteria.freeText())) {
            String like = "%" + criteria.freeText().trim() + "%";
            sql.append(" AND (pa.name LIKE ? OR d.notes LIKE ? OR CAST(d.")
                    .append(key).append(" AS CHAR) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return sql.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String insertSql() {
        return SqlStatements.insertStatement(table, insertColumns.toArray(String[]::new));
    }

    public String updateSql() {
        String ordinary = SqlStatements.updateStatement(
                table, key, updateColumns.toArray(String[]::new));
        return ordinary.replaceFirst(" SET ",
                " SET updated_at=CURRENT_TIMESTAMP(6), ") + " AND updated_at=?";
    }

    public String deleteSql() {
        return SqlStatements.deleteStatement(table, key);
    }

    public String deleteInRangeSql(int count) {
        return SqlStatements.deleteInRangeId(table, key, count);
    }

    /** The next document number of this kind. The four families are numbered separately. */
    public String maxIdSql() {
        return "SELECT COALESCE(MAX(" + key + "), 0) + 1 FROM " + table;
    }

    // ---- its lines -------------------------------------------------------------------

    public String lineSelectAllSql() {
        return SqlStatements.selectStatement(lineView);
    }

    public String lineSelectByDocumentSql() {
        return SqlStatements.selectStatementByColumnWhere(lineView, LINE_DOCUMENT);
    }

    public String lineSelectBetweenDocumentsSql() {
        return SqlStatements.selectStatement(lineView) + " WHERE " + LINE_DOCUMENT + " BETWEEN ? AND ?";
    }

    public String lineSelectByItemSql() {
        return SqlStatements.selectStatement(lineView) + " WHERE " + lineItem + " = ?";
    }

    public String lineInsertSql() {
        return SqlStatements.insertStatement(lineTable, lineColumns.toArray(String[]::new));
    }

    public String lineDeleteSql() {
        return SqlStatements.deleteStatement(lineTable, LINE_KEY);
    }

    /** Locks the current identities before an update computes its insert/update/delete delta. */
    public String lineIdsForUpdateSql() {
        return "SELECT " + LINE_KEY + " FROM " + lineTable
                + " WHERE " + LINE_DOCUMENT + "=? FOR UPDATE";
    }

    /**
     * Updates the mutable values of one line while also proving that the id still
     * belongs to this document. The document id is deliberately not movable.
     */
    public String lineUpdateSql() {
        String assignments = lineColumns.subList(1, lineColumns.size()).stream()
                .map(column -> column + "=?")
                .collect(java.util.stream.Collectors.joining(","));
        return "UPDATE " + lineTable + " SET " + assignments
                + " WHERE " + LINE_KEY + "=? AND " + LINE_DOCUMENT + "=?";
    }

    /** Deletes one removed line, scoped to the document whose edit is in progress. */
    public String lineDeleteOwnedSql() {
        return "DELETE FROM " + lineTable + " WHERE " + LINE_KEY + "=? AND " + LINE_DOCUMENT + "=?";
    }

    // ---- across the four --------------------------------------------------------------

    /**
     * Every year any document was written in. It is the one query that belongs to no
     * single family, and it named all four tables by hand before this.
     */
    public static String yearsSql() {
        StringBuilder sql = new StringBuilder();
        for (DocumentType type : DocumentType.values()) {
            DocumentTableSpec spec = of(type);
            sql.append(sql.isEmpty()
                            ? "SELECT YEAR(" + spec.dateColumn() + ") AS action_year FROM "
                            : "UNION\nSELECT YEAR(" + spec.dateColumn() + ") FROM ")
                    .append(spec.table())
                    .append('\n');
        }
        return sql + "ORDER BY action_year DESC";
    }
}
