package com.hamza.account.features.pricing;

/**
 * The statements a fill reads and writes with, pinned by {@code TierReportQueryTest}. A tier's column is
 * named only through {@link PriceTiers}, so only an identifier that class owns enters the SQL.
 * <p>
 * {@code items_units} names its unit {@code unit} (V1), not {@code unit_id} - the first MySQL run met
 * that where no unit test could.
 */
final class TierFillQuery {

    static final String ITEMS_SQL = """
            SELECT i.id, i.nameItem, u.unit_name, i.buy_price, i.sel_price1, i.sel_price2, i.sel_price3
            FROM items i
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            ORDER BY i.id""";

    /** An item's other units: the base unit is {@code items.unit_id}, not a row here (V5). */
    static final String UNITS_SQL = """
            SELECT iu.items_id, iu.unit AS unit_id, un.unit_name, iu.buy_price, iu.sel_price, iu.sel_price2,
                   iu.sel_price3
            FROM items_units iu
                     JOIN items i ON i.id = iu.items_id
                     JOIN units un ON un.unit_id = iu.unit
            WHERE iu.unit <> i.unit_id
            ORDER BY iu.items_id, iu.unit""";

    /** Items first, then their units - the order the unit prices save locks them in. */
    static final String LOCK_ITEMS_SQL = "SELECT id FROM items ORDER BY id FOR UPDATE";
    static final String LOCK_UNITS_SQL = "SELECT items_id FROM items_units ORDER BY items_id, unit FOR UPDATE";

    static String writeItemSql(int tierId) {
        String column = PriceTiers.itemColumn(tierId);
        return "UPDATE items SET " + column + " = ? WHERE id = ? AND " + column + " = ?";
    }

    static String writeUnitSql(int tierId) {
        String column = PriceTiers.unitColumn(tierId);
        return "UPDATE items_units SET " + column + " = ? WHERE items_id = ? AND unit = ? AND " + column + " = ?";
    }

    private TierFillQuery() {
    }
}
