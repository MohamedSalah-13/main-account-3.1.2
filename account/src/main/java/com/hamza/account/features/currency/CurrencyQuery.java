package com.hamza.account.features.currency;

/**
 * Every statement over {@code currency} and {@code currency_rate} (V80), pinned character for character
 * by {@code CurrencyQueryTest}.
 * <p>
 * <b>The rate in force is one definition, written twice for two shapes of question and held to each
 * other by that test</b>: {@link #RATE_ON_SQL} for one currency and {@link #RATES_IN_FORCE_SQL} for all
 * of them at once. Both say "the latest rate dated on the day or before it", both read the
 * {@code (currency_id, effective_date)} index V80's unique key builds, and neither ever answers a rate
 * dated after the day - which is the whole of what makes a rate entered tonight for tomorrow harmless
 * to today's figures.
 */
public final class CurrencyQuery {

    private static final String COLUMNS = """
            SELECT c.id,
                   c.code,
                   c.name,
                   c.symbol,
                   c.symbol_latin,
                   c.decimal_places,
                   c.is_base,
                   c.is_active,
                   c.sort_order
            FROM currency c
            """;

    /** The base first, then the shop's own order, then the code. */
    private static final String ORDER = "ORDER BY c.is_base DESC, c.sort_order, c.code";

    public static final String ALL_SQL = COLUMNS + ORDER;

    public static final String BY_ID_SQL = COLUMNS + "WHERE c.id = ?";

    public static final String BASE_SQL = COLUMNS + "WHERE c.is_base = 1";

    public static final String INSERT_SQL = """
            INSERT INTO currency (code, name, symbol, symbol_latin, decimal_places, is_active, sort_order, user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * The update names what the form owns. Not {@code is_base} - see {@link CurrencyDraft} - and not
     * {@code user_id}, which records who created the currency.
     */
    public static final String UPDATE_SQL = """
            UPDATE currency
            SET code = ?, name = ?, symbol = ?, symbol_latin = ?, decimal_places = ?, is_active = ?, sort_order = ?
            WHERE id = ?""";

    public static final String DELETE_SQL = "DELETE FROM currency WHERE id = ?";

    /**
     * Every currency row, locked in id order, before the base moves. A rate's insert takes a shared lock
     * on its currency's row through the foreign key, so while these are held no rate can be recorded -
     * and "no rate exists" stays true between being counted and the flag moving.
     */
    public static final String LOCK_ALL_SQL = "SELECT c.id FROM currency c ORDER BY c.id FOR UPDATE";

    public static final String CLEAR_BASE_SQL = "UPDATE currency SET is_base = 0 WHERE is_base = 1";

    /** {@code is_active = 1} restates the rule so a stopped currency cannot become the base by a race. */
    public static final String MARK_BASE_SQL = "UPDATE currency SET is_base = 1 WHERE id = ? AND is_active = 1";

    public static final String RATE_COUNT_SQL = "SELECT COUNT(*) FROM currency_rate";

    /** Treasuries in a currency other than the base (V81): while any exists, the base does not move. */
    public static final String FOREIGN_TREASURY_COUNT_SQL = "SELECT COUNT(*) FROM treasury WHERE currency_id IS NOT NULL";

    /** Active treasuries in one currency: while any exists, the currency is not stopped. */
    public static final String ACTIVE_TREASURY_COUNT_SQL =
            "SELECT COUNT(*) FROM treasury WHERE currency_id = ? AND is_active = 1";

    /**
     * The currency a rate is being written for, read under a shared lock: a base moving at the same
     * moment holds every currency row exclusively, so the two cannot interleave (see
     * {@link #LOCK_ALL_SQL}).
     */
    public static final String LOCK_FOR_RATE_SQL = COLUMNS + "WHERE c.id = ? FOR SHARE";

    private static final String RATE_COLUMNS = """
            SELECT r.id,
                   r.currency_id,
                   r.effective_date,
                   r.rate,
                   r.notes,
                   u.user_name AS entered_by,
                   r.created_at
            FROM currency_rate r
                     LEFT JOIN users u ON u.id = r.user_id
            """;

    /** One currency's rates, newest day first - the history panel. */
    public static final String RATES_OF_SQL = RATE_COLUMNS
            + "WHERE r.currency_id = ?\nORDER BY r.effective_date DESC";

    public static final String RATE_BY_ID_SQL = RATE_COLUMNS + "WHERE r.id = ?";

    /**
     * One currency's rate on a day, with the one it replaced. {@code LIMIT 2} over the index read
     * backwards from the day: the first row is the rate in force, the second what it replaced.
     */
    public static final String RATE_ON_SQL = """
            SELECT r.currency_id,
                   r.effective_date,
                   r.rate
            FROM currency_rate r
            WHERE r.currency_id = ?
              AND r.effective_date <= ?
            ORDER BY r.effective_date DESC
            LIMIT 2""";

    /**
     * Every currency's rate on a day, with the one it replaced: the latest row per currency among those
     * dated on the day or before it. A currency with none is absent - never a zero row.
     */
    public static final String RATES_IN_FORCE_SQL = """
            SELECT ranked.currency_id,
                   ranked.effective_date,
                   ranked.rate,
                   ranked.previous_rate
            FROM (SELECT r.currency_id,
                         r.effective_date,
                         r.rate,
                         LAG(r.rate) OVER (PARTITION BY r.currency_id ORDER BY r.effective_date) AS previous_rate,
                         ROW_NUMBER() OVER (PARTITION BY r.currency_id ORDER BY r.effective_date DESC) AS newest
                  FROM currency_rate r
                  WHERE r.effective_date <= ?) ranked
            WHERE ranked.newest = 1""";

    public static final String RATE_DAY_TAKEN_SQL =
            "SELECT COUNT(*) FROM currency_rate WHERE currency_id = ? AND effective_date = ? AND id <> ?";

    public static final String INSERT_RATE_SQL = """
            INSERT INTO currency_rate (currency_id, effective_date, rate, notes, user_id)
            VALUES (?, ?, ?, ?, ?)""";

    /** Not {@code currency_id} - see {@link ExchangeRateDraft} - and not who entered it. */
    public static final String UPDATE_RATE_SQL = """
            UPDATE currency_rate
            SET effective_date = ?, rate = ?, notes = ?
            WHERE id = ?""";

    public static final String DELETE_RATE_SQL = "DELETE FROM currency_rate WHERE id = ?";

    private CurrencyQuery() {
    }
}
