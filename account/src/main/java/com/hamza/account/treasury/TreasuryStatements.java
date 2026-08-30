package com.hamza.account.treasury;

/**
 * Every statement the treasury feature runs, in one place.
 * <p>
 * Same reason as {@code DocumentTableSpec} and {@code PartyLedgerSpec}: SQL spread
 * over DAOs is SQL nobody can pin. A merge that swaps two adjacent columns still
 * produces valid SQL - it just reports the wrong money - so the statements are
 * declared here and {@code TreasuryStatementsTest} holds them character for
 * character, together with the parameter count of each.
 * <p>
 * The columns are named rather than {@code SELECT *} on purpose: the view gains
 * columns as the plan advances (docs/treasury-plan.md), and a mapper reading by
 * position would follow them silently.
 */
public final class TreasuryStatements {

    public static final String BALANCE_VIEW = "treasury_current_balance";
    public static final String TRANSFERS_TABLE = "treasury_transfers";
    public static final String CASH_TABLE = "treasury_deposit_expenses";

    // ---- balances --------------------------------------------------------------

    private static final String BALANCE_COLUMNS = """
            id, t_name, treasury_type, is_active, sort_order, fee_percent,
                   opening, total_in, total_out, balance""";

    /** Every treasury, active or not, in the order the pickers and screens use. */
    public static final String SELECT_ALL_BALANCES = """
            SELECT %s
            FROM %s
            ORDER BY sort_order, id
            """.formatted(BALANCE_COLUMNS, BALANCE_VIEW);

    /** Only the treasuries a user may still choose - see {@code is_active}. */
    public static final String SELECT_ACTIVE_BALANCES = """
            SELECT %s
            FROM %s
            WHERE is_active = 1
            ORDER BY sort_order, id
            """.formatted(BALANCE_COLUMNS, BALANCE_VIEW);

    public static final String SELECT_BALANCE_BY_ID = """
            SELECT %s
            FROM %s
            WHERE id = ?
            """.formatted(BALANCE_COLUMNS, BALANCE_VIEW);

    /**
     * Serializes two people moving money out of the same treasury at once.
     * <p>
     * The balance is derived, so reading it and then inserting is a read-then-write on
     * a number nothing holds still: two withdrawals of 600 against a balance of 1000
     * both pass their check and both commit. Taking a row lock on the treasury itself
     * makes the second wait for the first and re-read - the same shape as
     * {@code StockTransferDao.lockSource}.
     */
    public static final String LOCK_TREASURY = """
            SELECT id
            FROM treasury
            WHERE id = ?
            FOR UPDATE
            """;

    // ---- transfers -------------------------------------------------------------

    public static final String INSERT_TRANSFER = """
            INSERT INTO treasury_transfers
                (treasury_from, treasury_to, amount, transfer_date, notes, user_id)
            VALUES
                (?, ?, ?, ?, ?, ?)
            """;

    /**
     * Read through the view that already carries both names, rather than joining
     * {@code treasury} twice here - {@code treasury_transfers_and_names} exists for
     * exactly this and had no reader at all.
     */
    public static final String SELECT_RECENT_TRANSFERS = """
            SELECT id, treasury_from, treasury_to, amount, transfer_date, notes,
                   treasury_name_from, treasury_name_to
            FROM treasury_transfers_and_names
            ORDER BY transfer_date DESC, id DESC
            LIMIT ?
            """;

    public static final String DELETE_TRANSFER = """
            DELETE FROM treasury_transfers
            WHERE id = ?
            """;

    // ---- deposits and withdrawals ----------------------------------------------

    public static final String INSERT_CASH_MOVEMENT = """
            INSERT INTO treasury_deposit_expenses
                (statement, date_inter, amount, description_data, deposit_or_expenses,
                 category, treasury_id, user_id)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    public static final String SELECT_RECENT_CASH_MOVEMENTS = """
            SELECT d.id, d.statement, d.date_inter, d.amount, d.description_data,
                   d.deposit_or_expenses, d.category, d.treasury_id, t.t_name
            FROM treasury_deposit_expenses d
                     JOIN treasury t ON t.id = d.treasury_id
            ORDER BY d.date_inter DESC, d.id DESC
            LIMIT ?
            """;

    /**
     * The owner's own movements over a period - what was paid in and what was taken
     * out. {@code category <> 'NORMAL'} rather than naming the two: a category added
     * later is the owner's until someone says otherwise, and being listed in a report
     * is safer than being silently left out of one.
     */
    public static final String SELECT_CAPITAL_MOVEMENTS = """
            SELECT d.id, d.statement, d.date_inter, d.amount, d.description_data,
                   d.deposit_or_expenses, d.category, d.treasury_id, t.t_name
            FROM treasury_deposit_expenses d
                     JOIN treasury t ON t.id = d.treasury_id
            WHERE d.category <> 'NORMAL'
              AND d.date_inter BETWEEN ? AND ?
            ORDER BY d.date_inter, d.id
            """;

    public static final String DELETE_CASH_MOVEMENT = """
            DELETE FROM treasury_deposit_expenses
            WHERE id = ?
            """;

    private TreasuryStatements() {
    }
}
