package com.hamza.account.features.inventory;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the inventory sheet. Two statements, both built from the same filter.
 * <p>
 * The screen used to be served by {@code ItemsDao.getProducts} - the item-editing
 * query - and paid for it twice. Once in the row mapper, which loads a whole
 * {@code ItemsModel} and with it the sub group, the unit, the item's other units,
 * its extra barcodes, its stock row and its image blob: five queries and a
 * {@code LONGBLOB} per row, none of which the sheet shows. And once in the totals,
 * which could only be reached by summing the loaded rows, so "إجمالي المخزون" could
 * never mean more than the page on screen.
 * <p>
 * Both queries here select what is displayed and nothing else, and
 * {@link #summary} aggregates in SQL over the whole filtered set. The filter is
 * built once in {@link #filter} and shared, which is what keeps the totals, the row
 * count and the rows describing the same set of items.
 *
 * <h2>Where the numbers come from</h2>
 * {@code quantity_items_table} already converts every invoice line to the item's
 * base unit ({@code quantity * type_value}), so the movement columns are directly
 * comparable. It is keyed by (item, stock), so the movements are pre-aggregated by
 * item in a subquery: that guarantees one row per item however many stock rows exist,
 * and - since the opening balance is added outside the aggregate - stops the opening
 * being counted once per stock row.
 * <p>
 * The opening balance comes from the selected warehouse row in
 * {@code quantity_items_table}. This keeps the report aligned with the per-warehouse
 * opening balances introduced by {@code V18__warehouse_opening_balances.sql}.
 */
public class InventoryDao extends AbstractDao<InventoryRow> {

    /**
     * Movements per item, base units, one row each. Mirrors the shape
     * {@code mini_quantity_view} uses over the same view.
     */
    private static final String MOVEMENTS = """
            (SELECT item_id,
                    SUM(first_balance)       AS opening,
                    SUM(quantityPurchase)   AS qty_purchase,
                    SUM(quantitySales)      AS qty_sales,
                    SUM(quantityPurchaseRe) AS qty_purchase_re,
                    SUM(quantitySalesRe)    AS qty_sales_re,
                    SUM(fromStock)          AS qty_from_stock,
                    SUM(toStock)            AS qty_to_stock,
                    SUM(adjustment)         AS qty_adjustment
             FROM quantity_items_table
             WHERE stock_id = ?
             GROUP BY item_id)
            """;

    private static final String FROM_CLAUSE = """
            FROM items i
                     JOIN %s q ON q.item_id = i.id
                     LEFT JOIN units u ON u.unit_id = i.unit_id
            """.formatted(MOVEMENTS);

    /**
     * What is left of the item, in its base unit.
     * <p>
     * {@code qty_adjustment} is what posted stock counts corrected the balance by,
     * signed, and it is added rather than subtracted: a count that found more than the
     * system said raises the balance. Added by {@code V8}.
     */
    private static final String BALANCE = """
            (q.opening + q.qty_purchase + q.qty_sales_re + q.qty_to_stock + q.qty_adjustment
                             - q.qty_sales - q.qty_purchase_re - q.qty_from_stock)""";

    private static final String SELECT_ROWS = """
            SELECT i.id,
                   i.nameItem,
                   i.barcode,
                   i.item_active,
                   i.buy_price,
                   i.sel_price1,
                   i.mini_quantity,
                   q.opening,
                   u.unit_name,
                   q.qty_purchase,
                   q.qty_sales,
                   q.qty_purchase_re,
                   q.qty_sales_re,
                   q.qty_from_stock,
                   q.qty_to_stock,
                   q.qty_adjustment,
                   %s AS balance
            """.formatted(BALANCE) + FROM_CLAUSE;

    /**
     * The counts mirror {@code StockLevel.of} - below zero, exactly zero, at or under
     * a minimum that was actually set - so the header cannot disagree with the alert
     * the sales screens raise. The arithmetic is on {@code DECIMAL} columns, so
     * comparing against zero is exact here in a way it would not be on doubles.
     */
    private static final String SELECT_SUMMARY = """
            SELECT COUNT(*)                                                        AS item_count,
                   COALESCE(SUM(%1$s), 0)                                          AS total_quantity,
                   COALESCE(SUM(%1$s * i.buy_price), 0)                            AS value_at_cost,
                   COALESCE(SUM(%1$s * i.sel_price1), 0)                           AS value_at_sale,
                   COALESCE(SUM(%1$s > 0 AND i.mini_quantity > 0
                                          AND %1$s <= i.mini_quantity), 0)         AS low_stock_count,
                   COALESCE(SUM(%1$s = 0), 0)                                      AS out_of_stock_count,
                   COALESCE(SUM(%1$s < 0), 0)                                      AS negative_count
            """.formatted(BALANCE) + FROM_CLAUSE;

    private static final String ORDER_BY = " ORDER BY i.id DESC ";

    public InventoryDao() {
        super();
    }

    /**
     * One page of the sheet.
     */
    public List<InventoryRow> rows(InventoryQuery query) throws DaoException {
        Filter filter = filter(query);
        List<Object> parameters = new ArrayList<>();
        parameters.add(query.stockId());
        parameters.addAll(filter.parameters());
        parameters.add(query.pageSize());
        parameters.add(query.offset());
        return queryForObjects(SELECT_ROWS + filter.sql() + ORDER_BY + " LIMIT ? OFFSET ? ",
                this::map, parameters.toArray());
    }

    /**
     * Every row the filter matches, unpaged - for printing and export.
     * <p>
     * Printing used to hand the report {@code tableView.getItems()}, so "طباعة" on a
     * shop with four thousand items printed fifty of them.
     */
    public List<InventoryRow> allRows(InventoryQuery query) throws DaoException {
        Filter filter = filter(query);
        List<Object> parameters = new ArrayList<>();
        parameters.add(query.stockId());
        parameters.addAll(filter.parameters());
        return queryForObjects(SELECT_ROWS + filter.sql() + ORDER_BY,
                this::map, parameters.toArray());
    }

    /**
     * The totals over everything the filter matches. One row, one round trip, no
     * items loaded.
     */
    public InventorySummary summary(InventoryQuery query) throws DaoException {
        Filter filter = filter(query);
        String sql = SELECT_SUMMARY + filter.sql();
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                List<Object> parameters = new ArrayList<>();
                parameters.add(query.stockId());
                parameters.addAll(filter.parameters());
                for (int i = 0; i < parameters.size(); i++) {
                    statement.setObject(i + 1, parameters.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return InventorySummary.EMPTY;
                    }
                    return new InventorySummary(
                            rs.getLong("item_count"),
                            rs.getDouble("total_quantity"),
                            rs.getDouble("value_at_cost"),
                            rs.getDouble("value_at_sale"),
                            rs.getLong("low_stock_count"),
                            rs.getLong("out_of_stock_count"),
                            rs.getLong("negative_count"));
                }
            }
        });
    }

    /**
     * The {@code WHERE} clause and its parameters, built once and shared by the rows,
     * the totals and the row count - which is what keeps all three describing the
     * same set of items.
     * <p>
     * This is the seam the screen's filters go through. Adding one is a condition in
     * this list; it reaches everything the screen shows without a query being
     * duplicated.
     * <p>
     * The search covers the three places an item's code can live -
     * {@code items.barcode}, {@code item_barcodes}, and a unit's own code in
     * {@code items_units} - the same three {@code ItemsDao} searches, so a carton
     * found by the code printed on it in the invoice screen is found here too. What
     * it does not carry over is that query's {@code LIMIT 50}: that cap belongs to a
     * lookup feeding an invoice line, and on a report it silently truncated both the
     * table and the total.
     */
    private Filter filter(InventoryQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        if (query.hasSearch()) {
            String like = "%" + query.search() + "%";
            conditions.add("""
                    (i.nameItem LIKE ?
                        OR i.barcode LIKE ?
                        OR i.id IN (SELECT item_id FROM item_barcodes WHERE barcode LIKE ?)
                        OR i.id IN (SELECT items_id FROM items_units WHERE items_barcode LIKE ?))""");
            parameters.add(like);
            parameters.add(like);
            parameters.add(like);
            parameters.add(like);
        }

        // The filter writes its own condition, and writes it against the one
        // definition of a balance there is rather than a second copy of the
        // arithmetic. See StockFilter for why it is a state and not a boolean.
        if (query.level().narrows()) {
            conditions.add("(" + query.level().condition(BALANCE) + ")");
        }

        // items carries the sub group; the group the user picks is the main one above
        // it, which is one level further out.
        if (query.hasGroup()) {
            conditions.add("i.sub_num IN (SELECT id FROM sub_group WHERE main_id = ?)");
            parameters.add(query.mainGroupId());
        }

        if (!query.includeInactive()) {
            conditions.add("i.item_active = 1");
        }

        if (conditions.isEmpty()) {
            return new Filter("", List.of());
        }
        return new Filter(" WHERE " + String.join(" AND ", conditions) + " ", parameters);
    }

    @Override
    public InventoryRow map(ResultSet rs) throws DaoException {
        try {
            return new InventoryRow(
                    rs.getInt("id"),
                    rs.getString("nameItem"),
                    rs.getString("barcode"),
                    // No row in units for the item's unit_id leaves this null rather
                    // than failing the whole page; the column simply reads empty.
                    rs.getString("unit_name") == null ? "" : rs.getString("unit_name"),
                    rs.getBoolean("item_active"),
                    rs.getDouble("opening"),
                    rs.getDouble("qty_purchase"),
                    rs.getDouble("qty_sales"),
                    rs.getDouble("qty_purchase_re"),
                    rs.getDouble("qty_sales_re"),
                    rs.getDouble("qty_from_stock"),
                    rs.getDouble("qty_to_stock"),
                    rs.getDouble("qty_adjustment"),
                    rs.getDouble("balance"),
                    rs.getDouble("buy_price"),
                    rs.getDouble("sel_price1"),
                    rs.getDouble("mini_quantity"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /**
     * One row per (item, warehouse) with a balance, for the cross-warehouse comparison
     * report - the question the single-{@code stockId} query above cannot answer,
     * because it is deliberately scoped to one warehouse at a time.
     */
    private static final String CROSS_WAREHOUSE_SQL = """
            SELECT i.id, i.nameItem, i.barcode, s.stock_name,
                   (q.first_balance + q.quantityPurchase + q.quantitySalesRe + q.toStock + q.adjustment
                        - q.quantitySales - q.quantityPurchaseRe - q.fromStock) AS balance
            FROM quantity_items_table q
                     JOIN items  i ON i.id = q.item_id
                     JOIN stocks s ON s.stock_id = q.stock_id
            ORDER BY i.nameItem, s.stock_name
            """;

    public List<StockBalanceRow> crossWarehouseBalances() throws DaoException {
        return withConnection(connection -> {
            List<StockBalanceRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(CROSS_WAREHOUSE_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new StockBalanceRow(resultSet.getInt("id"), resultSet.getString("nameItem"),
                            resultSet.getString("barcode"), resultSet.getString("stock_name"),
                            resultSet.getDouble("balance")));
                }
            }
            return rows;
        });
    }

    /** A {@code WHERE} clause and the values it needs, kept together. */
    private record Filter(String sql, List<Object> parameters) {
    }
}
