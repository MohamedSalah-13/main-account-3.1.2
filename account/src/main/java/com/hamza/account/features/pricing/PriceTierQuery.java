package com.hamza.account.features.pricing;

/** The statements over {@code type_price}, pinned by {@code PriceTierQueryTest}. */
final class PriceTierQuery {

    static final String ALL_SQL = """
            SELECT id, name, is_active, rule_source, rule_tier_id, rule_percent, rule_rounding
            FROM type_price
            ORDER BY id""";

    static final String LOCK_SQL = "SELECT id FROM type_price ORDER BY id FOR UPDATE";

    static final String UPDATE_SQL = """
            UPDATE type_price
            SET name = ?, is_active = ?, rule_source = ?, rule_tier_id = ?, rule_percent = ?, rule_rounding = ?
            WHERE id = ?""";

    /** A name no user types and no two rows share: {@code #1}, {@code #2}, {@code #3}. */
    static final String CLEAR_NAME_SQL = "UPDATE type_price SET name = CONCAT('#', id) WHERE id = ?";

    /** Stopped customers are left out: they are sold nothing, so no tier of theirs prices anything. */
    static final String CUSTOMERS_BY_TIER_SQL = """
            SELECT price_id, COUNT(*)
            FROM custom
            WHERE is_active = 1
            GROUP BY price_id""";

    private PriceTierQuery() {
    }
}
