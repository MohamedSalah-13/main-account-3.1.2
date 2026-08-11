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
 * is accident: the date column is {@code created_at} on one and {@code date_insert} on
 * the other, and the supplier's queries write their join in lower case.
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
 * @param createdColumn  when the row was entered, under its two names
 * @param insertColumns  the insert, in the order the DAO fills it
 * @param updateColumns  the update's SET clause; the key is the WHERE and is not here
 * @param openingBalance the opening-balance column, which the update drops while the
 *                       party has already moved
 */
public record PartyTableSpec(
        PartyKind kind,
        String table,
        String listJoin,
        String searchJoin,
        String createdColumn,
        List<String> insertColumns,
        List<String> updateColumns,
        String openingBalance) {

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
            List.of("name", "tel", "address", "notes", "limit_num", "first_balance", "price_id",
                    "user_id", "area_id"),
            List.of("name", "tel", "address", "notes", "limit_num", "first_balance", "price_id", "area_id"),
            "first_balance");

    public static final PartyTableSpec SUPPLIER = new PartyTableSpec(
            PartyKind.SUPPLIER, "suppliers",
            "join table_area on suppliers.area_id = table_area.id",
            "",
            "date_insert",
            List.of("name", "tel", "address", "notes", "first_balance", "user_id", "area_id"),
            List.of("name", "tel", "address", "notes", "first_balance", "area_id"),
            "first_balance");

    public PartyTableSpec {
        requireIdentifier(table);
        requireIdentifier(createdColumn);
        requireIdentifier(openingBalance);
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
        return SqlStatements.updateStatement(table, KEY, updateColumns.toArray(String[]::new));
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
        return SqlStatements.updateStatement(table, KEY, updateColumns.stream()
                .filter(column -> !column.equals(openingBalance))
                .toArray(String[]::new));
    }

    /** Where the opening balance sits in the array the update binds. */
    public int openingBalanceIndex() {
        return updateColumns.indexOf(openingBalance);
    }

    public String deleteSql() {
        return SqlStatements.deleteStatement(table, KEY);
    }

    // ---- the name box's search --------------------------------------------------------
    //
    // Three statements, tried in order: an exact id or telephone, then names that start
    // with what was typed, then names that contain it. Both parties run the same three.

    /** Nothing typed yet: the newest fifty. */
    public String searchAllSql() {
        return newestFirst() + " LIMIT " + SEARCH_LIMIT;
    }

    public String searchByNumberSql() {
        return """
                SELECT * FROM %1$s
                %2$sWHERE %1$s.id = ? OR %1$s.tel = ?
                ORDER BY
                    CASE
                        WHEN %1$s.id = ? THEN 0
                        WHEN %1$s.tel = ? THEN 1
                        ELSE 2
                    END,
                    %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT);
    }

    public String searchByPrefixSql() {
        return """
                SELECT * FROM %1$s
                %2$sWHERE %1$s.name LIKE ? OR %1$s.tel LIKE ?
                ORDER BY
                    CASE
                        WHEN %1$s.name LIKE ? THEN 0
                        WHEN %1$s.tel LIKE ? THEN 1
                        ELSE 2
                    END,
                    %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT);
    }

    public String searchByFragmentSql() {
        return """
                SELECT * FROM %1$s
                %2$sWHERE %1$s.name LIKE ? OR %1$s.tel LIKE ?
                ORDER BY %1$s.id DESC
                LIMIT %3$d
                """.formatted(table, line(searchJoin), SEARCH_LIMIT);
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
