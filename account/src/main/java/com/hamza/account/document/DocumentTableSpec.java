package com.hamza.account.document;

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

    /**
     * Every optional condition in {@code criteria} that is present is appended, each bound
     * value pushed onto {@code params} in the same order its {@code ?} appears - the date
     * range is the one condition always present. Runs against {@link #view}, so {@code name}
     * (party), {@code column_name} (delegate, sales-side only) and every other column named
     * here are already flat columns there, exactly as {@link #selectBetweenDatesSql} reads
     * from it.
     */
    public String searchSql(TotalsSearchCriteria criteria, List<Object> params) {
        StringBuilder sql = new StringBuilder(SqlStatements.selectStatement(view))
                .append(" WHERE ").append(dateColumn()).append(" BETWEEN ? AND ?");
        params.add(criteria.dateFrom().toString());
        params.add(criteria.dateTo().toString());

        if (criteria.invoiceNumber() != null) {
            sql.append(" AND ").append(key).append(" = ?");
            params.add(criteria.invoiceNumber());
        }
        if (hasText(criteria.partyName())) {
            sql.append(" AND name = ?");
            params.add(criteria.partyName());
        }
        if (hasDelegate() && hasText(criteria.delegateName())) {
            sql.append(" AND column_name = ?");
            params.add(criteria.delegateName());
        }
        if (criteria.invoiceType() != null) {
            sql.append(" AND invoice_type = ?");
            params.add(criteria.invoiceType().getId());
        }
        if (hasText(criteria.enteredByUsername())) {
            sql.append(" AND user_id = (SELECT id FROM users WHERE user_name = ?)");
            params.add(criteria.enteredByUsername());
        }
        if (criteria.minTotal() != null) {
            sql.append(" AND total >= ?");
            params.add(criteria.minTotal());
        }
        if (criteria.maxTotal() != null) {
            sql.append(" AND total <= ?");
            params.add(criteria.maxTotal());
        }
        if (hasText(criteria.freeText())) {
            String like = "%" + criteria.freeText().trim() + "%";
            sql.append(" AND (name LIKE ? OR notes LIKE ? OR CAST(").append(key).append(" AS CHAR) LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return sql.append(" ORDER BY ").append(key).append(" DESC").toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String insertSql() {
        return SqlStatements.insertStatement(table, insertColumns.toArray(String[]::new));
    }

    public String updateSql() {
        return SqlStatements.updateStatement(table, key, updateColumns.toArray(String[]::new));
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
