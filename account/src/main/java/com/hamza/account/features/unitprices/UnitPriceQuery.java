package com.hamza.account.features.unitprices;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every statement the unit prices screen reads and writes with, as text and the values bound to it.
 * <p>
 * Pure, so the placeholder counts are checkable without a database ({@code UnitPriceQueryTest}).
 * <p>
 * <b>Which items are listed is the items screen's own definition.</b> The {@code WHERE} starts from
 * {@link ItemCatalogSql#build} with the "more than one unit" condition and the typed search, so the
 * search behaves here exactly as it does there - digits are an id or a code matched exactly, and a
 * carton's own barcode finds its item - and "has units" is the predicate the items list counts with.
 * What this class adds on top is only what that screen has no word for.
 */
public final class UnitPriceQuery {

    private UnitPriceQuery() {
    }

    /** A statement and its values, in placeholder order. */
    public record Statement(String sql, List<Object> parameters) {
    }

    private static final String ITEM_COLUMNS = """
            SELECT items.id, items.barcode, items.nameItem, base.unit_name AS base_unit_name,
                   items.buy_price, items.sel_price1, items.sel_price2, items.sel_price3
            FROM items
                     LEFT JOIN units base ON base.unit_id = items.unit_id
            """;

    /** A unit besides the item's own, as {@code u}, joined to its item. */
    private static final String UNIT_ROWS = """
            SELECT u.items_id, u.unit, un.unit_name, u.quantity,
                   u.buy_price, u.sel_price, u.sel_price2, u.sel_price3
            FROM items_units u
                     JOIN items ON items.id = u.items_id
                     LEFT JOIN units un ON un.unit_id = u.unit
            """;

    /** The same exclusion {@link ItemCatalogSql#HAS_EXTRA_UNITS} makes: the item's own unit is not one of these. */
    private static final String NOT_THE_BASE_UNIT = "u.unit <> items.unit_id";

    private static final String OWN_PRICE_SET =
            "(u.buy_price > 0 OR u.sel_price > 0 OR u.sel_price2 > 0 OR u.sel_price3 > 0)";

    /**
     * A unit that sells at or below what it costs, on any tier it has a price for - both sides
     * worked out the way {@link UnitPriceLine#effective} works them out: its own price where it has
     * one, and otherwise the item's times the factor, rounded to the cent.
     */
    static final String BELOW_COST = """
            ((IF(u.sel_price > 0, u.sel_price, ROUND(items.sel_price1 * u.quantity, 2)) > 0
                  AND IF(u.sel_price > 0, u.sel_price, ROUND(items.sel_price1 * u.quantity, 2))
                      <= IF(u.buy_price > 0, u.buy_price, ROUND(items.buy_price * u.quantity, 2)))
              OR (IF(u.sel_price2 > 0, u.sel_price2, ROUND(items.sel_price2 * u.quantity, 2)) > 0
                  AND IF(u.sel_price2 > 0, u.sel_price2, ROUND(items.sel_price2 * u.quantity, 2))
                      <= IF(u.buy_price > 0, u.buy_price, ROUND(items.buy_price * u.quantity, 2)))
              OR (IF(u.sel_price3 > 0, u.sel_price3, ROUND(items.sel_price3 * u.quantity, 2)) > 0
                  AND IF(u.sel_price3 > 0, u.sel_price3, ROUND(items.sel_price3 * u.quantity, 2))
                      <= IF(u.buy_price > 0, u.buy_price, ROUND(items.buy_price * u.quantity, 2))))""";

    private static final String ORDER = " ORDER BY items.nameItem, items.id";

    /** One page of items. The page and {@link #count} are built from one {@link #where}. */
    public static Statement page(UnitPriceFilter filter, int limit, int offset) {
        Statement where = where(filter);
        List<Object> parameters = new ArrayList<>(where.parameters());
        parameters.add(limit);
        parameters.add(offset);
        return new Statement(ITEM_COLUMNS + where.sql() + ORDER + " LIMIT ? OFFSET ?", parameters);
    }

    /** Every item the filter matches, in page order - what "make all of them automatic" covers. */
    public static Statement all(UnitPriceFilter filter) {
        Statement where = where(filter);
        return new Statement(ITEM_COLUMNS + where.sql() + ORDER, where.parameters());
    }

    public static Statement count(UnitPriceFilter filter) {
        Statement where = where(filter);
        return new Statement("SELECT COUNT(*) FROM items" + where.sql(), where.parameters());
    }

    /**
     * The items a save is about to change, locked. {@code FOR UPDATE OF items} rather than a bare
     * {@code FOR UPDATE}: the unit-name join is there to read a name, and locking a row of
     * {@code units} would stop somebody renaming a carton for the length of a price save.
     */
    public static Statement lockItems(List<Integer> itemIds) {
        return new Statement(ITEM_COLUMNS + " WHERE items.id IN (" + marks(itemIds.size()) + ")"
                + " ORDER BY items.id FOR UPDATE OF items", List.copyOf(itemIds));
    }

    /** The units of these items, smallest first. {@code lock} adds {@code FOR UPDATE OF u}. */
    public static Statement units(List<Integer> itemIds, boolean lock) {
        return new Statement(UNIT_ROWS + " WHERE u.items_id IN (" + marks(itemIds.size()) + ") AND "
                + NOT_THE_BASE_UNIT + " ORDER BY u.items_id, u.quantity, u.unit"
                + (lock ? " FOR UPDATE OF u" : ""), List.copyOf(itemIds));
    }

    /**
     * An item's four prices and nothing else - the targeted write the items list's
     * {@code quickUpdate} is, without the name and code this screen does not show.
     */
    public static Statement updateItem(int itemId, Prices prices, int userId) {
        return new Statement(
                "UPDATE items SET buy_price = ?, sel_price1 = ?, sel_price2 = ?, sel_price3 = ?, user_id = ? WHERE id = ?",
                List.of(prices.buy(), prices.sell1(), prices.sell2(), prices.sell3(), userId, itemId));
    }

    /**
     * One unit's own prices, by the pair {@code items_units_item_unit_uk} is unique on.
     * <p>
     * Never through {@code ItemsDao.update}: that method deletes an item's unit rows and inserts
     * them again from the model, and this screen's rows carry no barcode and no unit list.
     */
    public static Statement updateUnit(int itemId, int unitId, Prices prices, int userId) {
        return new Statement("""
                UPDATE items_units SET buy_price = ?, sel_price = ?, sel_price2 = ?, sel_price3 = ?, user_id = ?
                WHERE items_id = ? AND unit = ?""",
                List.of(prices.buy(), prices.sell1(), prices.sell2(), prices.sell3(), userId, itemId, unitId));
    }

    static Statement where(UnitPriceFilter filter) {
        UnitPriceFilter safe = filter == null ? UnitPriceFilter.EMPTY : filter;
        ItemCatalogSql.Statement catalog = ItemCatalogSql.build(ItemCatalogFilter.EMPTY
                .withSearch(safe.search())
                .withMultipleUnits(ItemCatalogFilter.Tristate.YES));
        StringBuilder sql = new StringBuilder(catalog.where());
        List<Object> parameters = new ArrayList<>(catalog.whereParameters());

        switch (safe.state()) {
            case MANUAL -> sql.append(" AND EXISTS (SELECT 1 FROM items_units u WHERE u.items_id = items.id AND ")
                    .append(NOT_THE_BASE_UNIT).append(" AND ").append(OWN_PRICE_SET).append(")");
            case AUTOMATIC -> sql.append(" AND NOT EXISTS (SELECT 1 FROM items_units u WHERE u.items_id = items.id AND ")
                    .append(NOT_THE_BASE_UNIT).append(" AND ").append(OWN_PRICE_SET).append(")");
            case ANY -> {
            }
        }
        if (safe.belowCostOnly()) {
            sql.append(" AND EXISTS (SELECT 1 FROM items_units u WHERE u.items_id = items.id AND ")
                    .append(NOT_THE_BASE_UNIT).append(" AND ").append(BELOW_COST).append(")");
        }
        if (!safe.itemIds().isEmpty()) {
            List<Integer> ids = safe.itemIds().stream().sorted().toList();
            sql.append(" AND items.id IN (").append(marks(ids.size())).append(")");
            parameters.addAll(ids);
        }
        return new Statement(sql.toString(), parameters);
    }

    /** Callers never pass an empty list: {@code IN ()} is not SQL, and the repository answers those itself. */
    private static String marks(int size) {
        if (size <= 0) throw new IllegalArgumentException("an IN list needs at least one id");
        return String.join(",", Collections.nCopies(size, "?"));
    }
}
