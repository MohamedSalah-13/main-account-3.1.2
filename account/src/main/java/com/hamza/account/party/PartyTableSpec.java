package com.hamza.account.party;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.SqlStatements;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Where a customer or a supplier lives, and what its columns are called.
 * <p>
 * The two are the same row under two names: an id, a name, a telephone, an address,
 * notes, an opening balance, an area and who entered it. A customer has two columns more
 * - the credit limit and which price tier they buy at - and that is the whole of the
 * difference. Everything else that differs between {@code custom} and {@code suppliers}
 * is accident, and less of it every time: the date column was {@code created_at} on one
 * and {@code date_insert} on the other until {@code V10__supplier_created_at.sql}, and
 * the supplier's queries still write their join in lower case.
 * <p>
 * Collecting it here is what lets one statement serve both, the way
 * {@link com.hamza.account.document.DocumentTableSpec} does for the four documents.
 * {@code PartyDaoStatementsTest} pins every statement, so this file has to produce them
 * character for character.
 *
 * @param kind           which party this describes
 * @param table          the table written to
 * @param listJoin       the area join the listing query carries, or empty. It is a
 *                       {@code LEFT} join: it is there to read the area's name, and a
 *                       customer whose area row has been deleted is still a customer.
 *                       It was an inner join, which quietly dropped them from every
 *                       list and every search while the supplier in the same state
 *                       stayed - the supplier's queries never joined at all
 * @param searchJoin     the area join the search, paging and by-id queries carry, or
 *                       empty. Empty for suppliers, whose {@code map} looks the area up
 *                       with a query of its own
 * @param createdColumn  when the row was entered. One name on both sides since V10;
 *                       it stays a field because the tables are still two
 * @param insertColumns  the insert, in the order the DAO fills it
 * @param updateColumns  the update's SET clause; the key is the WHERE and is not here
 * @param openingBalance the opening-balance column, which the update drops while the
 *                       party has already moved
 * @param openingDate    the date that balance is as at (V56). It is dropped by the
 *                       same lock and for the same reason: re-dating a closed opening
 *                       entry moves it in the history exactly as rewriting its amount
 *                       would, and a guard that held one and not the other would be a
 *                       way round itself
 */
public record PartyTableSpec(
        PartyKind kind,
        String table,
        String listJoin,
        String searchJoin,
        String createdColumn,
        List<String> insertColumns,
        List<String> updateColumns,
        String openingBalance,
        String openingDate) {

    /** As in {@code LockedDocument}: these are concatenated into SQL, so they are checked. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The key, the name and the telephone are spelled the same in both tables. */
    public static final String KEY = "id";
    public static final String NAME = "name";
    public static final String TEL = "tel";

    /** How many rows a search returns. The name box is a picker, not a report. */
    public static final int SEARCH_LIMIT = 50;

    public static final PartyTableSpec CUSTOMER = new PartyTableSpec(
            PartyKind.CUSTOMER, "custom",
            "LEFT JOIN table_area ON custom.area_id = table_area.id",
            "LEFT JOIN table_area ON custom.area_id = table_area.id",
            "created_at",
            List.of("name", "tel", "address", "notes", "limit_num", "first_balance",
                    "opening_balance_date", "price_id", "user_id", "area_id",
                    "email", "tax_number", "payment_terms_days", "default_delegate_id", "is_active"),
            List.of("name", "tel", "address", "notes", "limit_num", "first_balance",
                    "opening_balance_date", "price_id", "area_id",
                    "email", "tax_number", "payment_terms_days", "default_delegate_id", "is_active"),
            "first_balance", "opening_balance_date");

    public static final PartyTableSpec SUPPLIER = new PartyTableSpec(
            PartyKind.SUPPLIER, "suppliers",
            "join table_area on suppliers.area_id = table_area.id",
            "",
            "created_at",
            List.of("name", "tel", "address", "notes", "first_balance", "opening_balance_date",
                    "user_id", "area_id",
                    "email", "tax_number", "payment_terms_days", "is_active"),
            List.of("name", "tel", "address", "notes", "first_balance", "opening_balance_date",
                    "area_id",
                    "email", "tax_number", "payment_terms_days", "is_active"),
            "first_balance", "opening_balance_date");

    public PartyTableSpec {
        requireIdentifier(table);
        requireIdentifier(createdColumn);
        requireIdentifier(openingBalance);
        requireIdentifier(openingDate);
        insertColumns = List.copyOf(insertColumns);
        updateColumns = List.copyOf(updateColumns);
        insertColumns.forEach(PartyTableSpec::requireIdentifier);
        updateColumns.forEach(PartyTableSpec::requireIdentifier);
    }

    private static void requireIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Not an identifier: " + identifier);
        }
    }

    public static PartyTableSpec of(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? CUSTOMER : SUPPLIER;
    }

    // ---- listing and lookup ----------------------------------------------------------

    public String selectAllSql() {
        return "SELECT * FROM " + table + inline(listJoin);
    }

    /**
     * One party by its id. It carries {@link #searchJoin} rather than {@link #listJoin}:
     * for the supplier those differ, and which join a statement carries is a thing to
     * decide deliberately and not to acquire by sharing a statement.
     */
    public String selectByIdSql() {
        return searchFrom() + " WHERE " + table + "." + KEY + " = ?";
    }

    public String selectByNameSql() {
        return searchFrom() + " WHERE " + table + "." + NAME + " = ?";
    }

    public String countSql() {
        return "SELECT COUNT(*) FROM " + table;
    }

    /** One page of the table, newest first. */
    public String pageSql() {
        return newestFirst() + " LIMIT ? OFFSET ?";
    }

    // ---- writing ---------------------------------------------------------------------

    public String insertSql() {
        return SqlStatements.insertStatement(table, insertColumns.toArray(String[]::new));
    }

    public String updateSql() {
        return optimisticUpdate(updateColumns);
    }

    /**
     * The same update with the opening balance left out.
     * <p>
     * It is the one figure on the row with no date on it - a statement is
     * {@code first_balance + invoices - payments} - so once the party has moved it is a
     * closed entry, and the correction is a new dated movement rather than a rewrite of
     * what they owed at every earlier date.
     */
    public String updateWithoutOpeningSql() {
        return optimisticUpdate(updateColumns.stream()
                .filter(column -> !openingColumns().contains(column)).toList());
    }

    /**
     * The columns the opening-balance lock takes out together: the amount and its date.
     * <p>
     * They are one entry, not two fields that happen to be near each other. A statement
     * is {@code first_balance} placed at {@code opening_balance_date} plus the movements
     * after it, so moving the date moves the entry through the history precisely as
     * changing the amount would - it is the same edit said differently, and letting the
     * date through would leave the guard guarding half of what it names.
     */
    public List<String> openingColumns() {
        return List.of(openingBalance, openingDate);
    }

    private String optimisticUpdate(List<String> columns) {
        String assignments = columns.stream()
                .map(column -> column + "=?")
                .collect(java.util.stream.Collectors.joining(","));
        return "UPDATE " + table + " SET updated_at=CURRENT_TIMESTAMP(6)," + assignments
                + " WHERE " + KEY + "=? AND updated_at=?";
    }

    public String updatedAtSql() {
        return "SELECT updated_at FROM " + table + " WHERE " + KEY + "=?";
    }

    /** Where the opening balance sits in the array the update binds. */
    public int openingBalanceIndex() {
        return updateColumns.indexOf(openingBalance);
    }

    /**
     * Where {@link #openingColumns()} sit in that array, highest index first.
     * <p>
     * Descending because the caller removes them one at a time and removing a low index
     * first would shift every one after it - the off-by-one
     * {@code OpeningBalanceGuard.without} exists to make impossible.
     */
    public List<Integer> openingColumnIndexes() {
        return openingColumns().stream()
                .map(updateColumns::indexOf)
                .filter(index -> index >= 0)
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
    }

    public String deleteSql() {
        return SqlStatements.deleteStatement(table, KEY);
    }

    // ---- the name box's search --------------------------------------------------------
    //
    // Three statements, tried in order: an exact id or telephone, then names that start
    // with what was typed, then names that contain it. Both parties run the same three.

    /**
     * The column {@code is_active} narrows these three by, and the one question it
     * answers.
     * <p>
     * <b>A stopped party may not be sold to and must still be collected from.</b> Those
     * are two different screens asking the same search two different questions, which is
     * why the scope is a parameter and not a property of the statement: the invoice
     * picker passes {@link PartySearchScope#ACTIVE_ONLY} - a party you have stopped
     * dealing with should not be reachable by typing three letters into a new invoice -
     * while the collection screen passes {@link PartySearchScope#EVERYONE}, because a
     * debt does not stop being owed when you stop selling. Filtering here for both would
     * make an old debt uncollectable through the only screen that collects, and the
     * parties list keeps {@code EVERYONE} too or there would be no way back to the row
     * that switches a party on again.
     */
    public enum PartySearchScope {
        /** Every party, stopped ones included. Lists, and anything settling a debt. */
        EVERYONE,
        /** Only parties still dealt with. Anything starting new business. */
        ACTIVE_ONLY;

        boolean narrows() {
            return this == ACTIVE_ONLY;
        }
    }

    /** Nothing typed yet: the newest fifty. */
    public String searchAllSql(PartySearchScope scope) {
        return searchFrom()
               + (scope.narrows() ? " WHERE " + table + ".is_active = 1" : "")
               + " ORDER BY " + table + "." + KEY + " DESC"
               + " LIMIT " + SEARCH_LIMIT;
    }

    public String searchByNumberSql(PartySearchScope scope) {
        return """
                SELECT * FROM %1$s
                %2$sWHERE (%1$s.id = ? OR %1$s.tel = ?)%4$s
                ORDER BY
                    CASE
                        WHEN %1$s.id = ? THEN 0
                        WHEN %1$s.tel = ? THEN 1
                        ELSE 2
                    END,
                    %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT, activeClause(scope));
    }

    public String searchByPrefixSql(PartySearchScope scope) {
        return """
                SELECT * FROM %1$s
                %2$sWHERE (%1$s.name LIKE ? OR %1$s.tel LIKE ?)%4$s
                ORDER BY
                    CASE
                        WHEN %1$s.name LIKE ? THEN 0
                        WHEN %1$s.tel LIKE ? THEN 1
                        ELSE 2
                    END,
                    %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT, activeClause(scope));
    }

    public String searchByFragmentSql(PartySearchScope scope) {
        return """
                SELECT * FROM %1$s
                %2$sWHERE (%1$s.name LIKE ? OR %1$s.tel LIKE ?)%4$s
                ORDER BY %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT, activeClause(scope));
    }

    /**
     * The status condition, or nothing.
     * <p>
     * The two text conditions are parenthesised whether or not this is added: {@code a OR
     * b AND c} is {@code a OR (b AND c)} in SQL, so appending the status to an unbracketed
     * {@code OR} would filter the telephone match and leave the name match wide open -
     * a stopped party would drop out of a search by phone number and stay in a search by
     * name, which reads on screen as the filter working intermittently.
     */
    private String activeClause(PartySearchScope scope) {
        return scope.narrows() ? " AND " + table + ".is_active = 1" : "";
    }

    private String newestFirst() {
        return searchFrom() + " ORDER BY " + table + "." + KEY + " DESC";
    }

    private String searchFrom() {
        return "SELECT * FROM " + table + inline(searchJoin);
    }

    private static String inline(String join) {
        return join.isEmpty() ? "" : " " + join;
    }

    private static String line(String join) {
        return join.isEmpty() ? "" : join + "\n";
    }
}
