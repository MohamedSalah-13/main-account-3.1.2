package com.hamza.account.party;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.SqlStatements;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Where a party's account movements live, and what their columns are called.
 * <p>
 * Only payments are stored in these tables. The invoice side of a statement is not:
 * {@code account_customer_table} unions {@code customers_accounts} with
 * {@code total_sales}, which is why a saved invoice never reaches the period lock here
 * and is guarded at {@code TotalsSalesDao} instead.
 * <p>
 * As with {@link PartyTableSpec}, the customer's ledger and the supplier's are the same
 * table twice - and now in the same way. The customer's update used to write
 * {@code user_id} where the supplier's did not, so the same edit restamped one payment
 * and left the other alone. {@code user_id} on a movement records <b>who entered it</b>,
 * which is the supplier's reading and the one the inserts already agree on; who changed
 * it afterwards is what {@code audit_log} is for, and the trigger writes that row whether
 * anyone asks or not.
 *
 * @param kind          which party's ledger this describes
 * @param table         the table written to - payments only
 * @param view          the view read from, which unions the payments with the invoices
 * @param totalsView    one row per party, already summed
 * @param partyTable    the party table the view is joined to for the name
 * @param ledgerAlias   what the statement calls the ledger, {@code act} or {@code ac}
 * @param partyAlias    what it calls the party
 * @param createdColumn when the row was entered, under its two names
 * @param insertColumns the insert, in the order the DAO fills it
 * @param updateColumns the update's SET clause; the key is the WHERE and is not here.
 *                      Neither carries {@code user_id}: an edit does not change who
 *                      entered the payment
 * @param withArea      whether a summary of this ledger carries the party's area. The
 *                      customer's does - the screen groups receivables by area - and the
 *                      supplier's has nowhere to put it
 */
public record PartyLedgerSpec(
        PartyKind kind,
        String table,
        String view,
        String totalsView,
        String partyTable,
        String ledgerAlias,
        String partyAlias,
        String createdColumn,
        List<String> insertColumns,
        List<String> updateColumns,
        boolean withArea) {

    /** As in {@code LockedDocument}: these are concatenated into SQL, so they are checked. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** The key of a movement, and the column pointing at whose account it is. */
    public static final String KEY = "account_num";
    public static final String PARTY = "account_code";

    /**
     * {@code information = 2} is a payment rather than an invoice line - the view carries
     * both, and a party's own account screen lists only what was paid.
     */
    public static final String PAYMENTS_ONLY = "information =2";

    public static final PartyLedgerSpec CUSTOMER = new PartyLedgerSpec(
            PartyKind.CUSTOMER, "customers_accounts", "account_customer_table",
            "account_customer_totals", "custom", "act", "c", "created_at",
            List.of("account_code", "account_date", "paid", "notes", "numberInv", "treasury_id",
                    "account_num", "user_id"),
            // No user_id, as on the supplier's side: editing a payment does not change
            // who entered it.
            List.of("account_code", "account_date", "paid", "notes", "numberInv", "treasury_id"),
            true);

    public static final PartyLedgerSpec SUPPLIER = new PartyLedgerSpec(
            PartyKind.SUPPLIER, "suppliers_accounts", "account_suppliers_table",
            "account_suppliers_totals", "suppliers", "ac", "s", "date_insert",
            List.of("account_code", "account_date", "paid", "notes", "numberInv", "treasury_id",
                    "account_num", "user_id"),
            List.of("account_code", "account_date", "paid", "notes", "numberInv", "treasury_id"),
            false);

    public PartyLedgerSpec {
        for (String identifier : new String[]{table, view, totalsView, partyTable, ledgerAlias,
                partyAlias, createdColumn}) {
            requireIdentifier(identifier);
        }
        insertColumns = List.copyOf(insertColumns);
        updateColumns = List.copyOf(updateColumns);
        insertColumns.forEach(PartyLedgerSpec::requireIdentifier);
        updateColumns.forEach(PartyLedgerSpec::requireIdentifier);
    }

    private static void requireIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Not an identifier: " + identifier);
        }
    }

    public static PartyLedgerSpec of(PartyKind kind) {
        return kind == PartyKind.CUSTOMER ? CUSTOMER : SUPPLIER;
    }

    /**
     * Whether editing a movement rewrites {@code user_id}. False on both sides: the
     * column records who entered the payment, and an edit does not make someone else
     * the one who entered it.
     */
    public boolean updateRecordsTheUser() {
        return updateColumns.contains("user_id");
    }

    // ---- the statement -----------------------------------------------------------
    //
    // One movement per row, with the amount worked out and the party's name joined in.
    // Every listing starts from it.

    public String statementSql() {
        String a = ledgerAlias;
        return """
                SELECT %1$s.account_num,
                       %1$s.account_code,
                       %1$s.account_date,
                       %1$s.purchase,
                       %1$s.discount,
                       %1$s.paid,
                       ROUND(%1$s.purchase - %1$s.discount - %1$s.paid) as amount,
                       %1$s.notes,
                       %2$s.name,
                       %1$s.information,
                       %1$s.type,
                       %1$s.%3$s,
                       %1$s.treasury_id,
                       %1$s.numberInv
                FROM %4$s %1$s
                         JOIN %5$s %2$s ON %1$s.account_code = %2$s.id"""
                .formatted(a, partyAlias, createdColumn, view, partyTable);
    }

    /** Everything, oldest entry first. */
    public String selectAllSql() {
        return statementSql() + " ORDER BY " + ledgerAlias + "." + createdColumn;
    }

    /** One party's payments - not their invoices, which the view also carries. */
    public String selectByPartySql() {
        return statementSql() + " WHERE " + ledgerAlias + "." + PARTY + " = ? and "
                + ledgerAlias + "." + PAYMENTS_ONLY;
    }

    // ---- writing -------------------------------------------------------------------

    public String insertSql() {
        return SqlStatements.insertStatement(table, insertColumns.toArray(String[]::new));
    }

    public String updateSql() {
        return SqlStatements.updateStatement(table, KEY, updateColumns.toArray(String[]::new));
    }

    public String deleteSql() {
        return SqlStatements.deleteStatement(table, KEY);
    }

    // ---- the rest --------------------------------------------------------------------

    /** One movement with its treasury's name, which the edit screen shows. */
    public String selectForUpdateSql() {
        return SqlStatements.selectStatement(table)
                .concat(" join treasury t on t.id = ").concat(table).concat(".treasury_id ")
                .concat(" WHERE ").concat(KEY).concat(" = ?");
    }

    /** The raw rows of one party, without the view's invoice side. */
    public String selectByPartyCodeSql() {
        return SqlStatements.selectStatementByColumnWhere(table, PARTY);
    }

    public String totalsSql() {
        return SqlStatements.selectStatement(totalsView).concat(" order by name ");
    }

    /**
     * The same one-row-per-party summary as {@link #totalsSql()}, restricted to a period.
     * <p>
     * It has to be a statement of its own rather than a {@code WHERE} on the view: the
     * view has already summed every movement there has ever been, and a total cannot be
     * filtered after the fact.
     * <p>
     * Two things it inherits from the query it replaced, both worth knowing. It selects
     * the same columns the totals view does - including the area, for a customer, which
     * is what the mapper reads and what the old query forgot, so a dated summary came
     * back empty however many payments were in the period. And it keeps
     * {@code purchase > 0}: a party who only paid in that period, and bought nothing,
     * is not on the list.
     */
    public String totalsBetweenDatesSql() {
        String a = ledgerAlias;
        return """
                SELECT %1$s.account_code,
                       %2$s.name,
                       SUM(%1$s.purchase)                                                   AS purchase,
                       SUM(%1$s.discount)                                                   AS discount,
                       SUM(%1$s.paid)                                                       AS paid,
                       ROUND(SUM(%1$s.purchase) - SUM(%1$s.discount) - SUM(%1$s.paid), 2)   AS amount,
                       MAX(%1$s.account_date)                                               AS account_date%3$s
                FROM %4$s %1$s
                         JOIN %5$s %2$s ON %1$s.account_code = %2$s.id%6$s
                WHERE %1$s.account_date BETWEEN ? AND ? AND %1$s.purchase > 0
                GROUP BY %1$s.account_code, %2$s.name%7$s"""
                .formatted(a, partyAlias,
                        withArea ? ",\n                       ta.id                                                                AS area_id,"
                                + "\n                       ta.area_name                                                         AS area_name" : "",
                        view, partyTable,
                        // LEFT, as everywhere else the area is joined: a party whose area
                        // row was deleted still owes what they owe.
                        withArea ? "\n                         LEFT JOIN table_area ta ON ta.id = " + partyAlias + ".area_id" : "",
                        withArea ? ", ta.id, ta.area_name" : "");
    }

    public String betweenDatesSql() {
        return "SELECT * FROM " + table + " ca\n"
                + "join " + partyTable + " c on c.id = ca." + PARTY + "\n"
                + "where ca.account_date between ? and ? order by ca.account_date ";
    }
}
