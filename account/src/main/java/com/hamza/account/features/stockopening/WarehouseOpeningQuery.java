package com.hamza.account.features.stockopening;

import com.hamza.account.features.items.WarehouseOpeningBalance;

/**
 * The statements behind the opening-balances screen, pinned by {@code WarehouseOpeningQueryTest}.
 * <p>
 * The page and its count share one {@code WHERE}, so the count cannot describe a different list from
 * the rows - {@code ItemsDao.catalogQuery}'s rule. Whether a row has moved is
 * {@link WarehouseOpeningBalance#MOVED}, the text the save's own check counts with: a row the screen
 * offers as open is one the save will accept, by construction rather than by two queries agreeing.
 */
public final class WarehouseOpeningQuery {

    static final String FROM = """
            FROM items_stock ist
                     JOIN items i ON i.id = ist.item_id
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            """;

    /**
     * {@code (stock, text, like, text, text, text, unmovedOnly)}. A code is matched whole - its own,
     * an extra one or a unit's, as the transfer history matches it - and a name by part, with its
     * wildcards escaped.
     */
    static final String WHERE = """
            WHERE ist.stock_id = ?
              AND (? IS NULL
                   OR i.nameItem LIKE ? ESCAPE '!'
                   OR i.barcode = ?
                   OR i.id IN (SELECT item_id FROM item_barcodes WHERE barcode = ?)
                   OR i.id IN (SELECT items_id FROM items_units WHERE items_barcode = ?))
              AND (? = 0 OR NOT %s)
            """.formatted(WarehouseOpeningBalance.MOVED);

    /** {@link #WHERE}'s values, then the limit and the offset. */
    public static final String PAGE = "SELECT i.id, i.barcode, i.nameItem, u.unit_name, ist.first_balance, "
            + WarehouseOpeningBalance.MOVED + " AS moved\n" + FROM + WHERE + "ORDER BY i.nameItem, i.id\nLIMIT ? OFFSET ?";

    /** {@link #WHERE}'s values. */
    public static final String COUNT = "SELECT COUNT(*)\n" + FROM + WHERE;

    private WarehouseOpeningQuery() {
    }

    /** Every value {@link #WHERE} binds, in the order it binds them. */
    public static Object[] whereValues(WarehouseOpeningFilter filter) {
        String text = filter.text();
        String like = text == null ? null : pattern(text);
        return new Object[]{filter.stockId(), text, like, text, text, text, filter.unmovedOnly() ? 1 : 0};
    }

    /** A contains-match with {@code !} as the escape, so the user's {@code %} and {@code _} are literal. */
    static String pattern(String text) {
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
