package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the whole reporting catalogue in one statement.
 * <p>
 * The statement is assembled from {@link ItemCatalogSql}, which is also what the items
 * screen filters with - so "the drinks group, active only" means the same rows in a report
 * as it does in the list the report was opened from. A report that built its own
 * {@code WHERE} would be a second opinion on what a filter means, and two opinions is how
 * the treasury ended up with three balances.
 */
public final class JdbcCatalogFactRepository implements CatalogFactRepository {

    /**
     * One row per item across every warehouse.
     * <p>
     * Pre-aggregated by {@code item_id} for the same reason {@code ItemsDao} pre-aggregates
     * it: {@code quantity_items_table} is keyed by (item, stock), so joining it raw returns
     * one row per warehouse and a report would count a two-warehouse item twice and halve
     * its balance.
     */
    private static final String MOVEMENTS = """
            (SELECT item_id,
                    SUM(quantityPurchase)   AS quantityPurchase,
                    SUM(quantitySales)      AS quantitySales,
                    SUM(quantityPurchaseRe) AS quantityPurchaseRe,
                    SUM(quantitySalesRe)    AS quantitySalesRe,
                    SUM(fromStock)          AS fromStock,
                    SUM(toStock)            AS toStock,
                    SUM(adjustment)         AS adjustment
             FROM quantity_items_table
             GROUP BY item_id)
            """;

    /**
     * The most recent date each item appeared on any document.
     * <p>
     * Four families, four column names - {@code num} on the two invoices and {@code item_id}
     * on the two returns, with the returns' header key called {@code id} where the invoices'
     * is {@code invoice_number}. That spread is what {@code ItemReferenceRegistry} exists to
     * remember, and getting one of them wrong here does not fail: it silently reports an
     * item as unused because the one table it moved in was the one left out.
     * <p>
     * All four, not sales alone, because "unused" means untouched. An item that was bought
     * and never sold is idle capital and belongs in the report; an item that was bought
     * last week is not unused, it is unsold, and saying otherwise would have the owner
     * writing off stock that has only just arrived.
     */
    private static final String LAST_MOVEMENT = """
            (SELECT item_id, MAX(moved_on) AS moved_on FROM (
                 SELECT s.num AS item_id, ts.invoice_date AS moved_on
                   FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number
                 UNION ALL
                 SELECT p.num, tb.invoice_date
                   FROM purchase p JOIN total_buy tb ON tb.invoice_number = p.invoice_number
                 UNION ALL
                 SELECT sr.item_id, tsr.invoice_date
                   FROM sales_re sr JOIN total_sales_re tsr ON tsr.id = sr.invoice_number
                 UNION ALL
                 SELECT pr.item_id, tbr.invoice_date
                   FROM purchase_re pr JOIN total_buy_re tbr ON tbr.id = pr.invoice_number
             ) movements GROUP BY item_id)
            """;

    private static final String SELECT = """
            SELECT items.id                AS item_id,
                   items.barcode           AS barcode,
                   items.nameItem          AS name_item,
                   items.buy_price         AS buy_price,
                   items.sel_price1        AS sel_price1,
                   items.mini_quantity     AS mini_quantity,
                   items.item_active       AS item_active,
                   items.item_has_validity AS item_has_validity,
                   sg.id                   AS sub_group_id,
                   sg.name                 AS sub_group_name,
                   mg.id                   AS main_group_id,
                   mg.name_g               AS main_group_name,
                   u.unit_name             AS unit_name,
            """;

    @Override
    public List<CatalogFact> facts(ItemCatalogFilter filter, boolean withLastMovement) throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        String sql = SELECT
                + "       " + ItemCatalogSql.BALANCE + " AS balance,\n"
                + (withLastMovement ? "       lm.moved_on AS moved_on\n" : "       NULL AS moved_on\n")
                + "FROM items\n"
                + "         JOIN " + MOVEMENTS + " ip ON items.id = ip.item_id\n"
                + "         LEFT JOIN sub_group sg ON sg.id = items.sub_num\n"
                + "         LEFT JOIN main_group mg ON mg.id = sg.main_id\n"
                + "         LEFT JOIN units u ON u.unit_id = items.unit_id\n"
                + (withLastMovement ? "         LEFT JOIN " + LAST_MOVEMENT + " lm ON lm.item_id = items.id\n" : "")
                + query.where()
                // Ordered by group and then by name, which is the order every one of these
                // reports wants to print in. The search ranking the items list uses is
                // meaningless here - nobody searched.
                + "\nORDER BY mg.name_g, sg.name, items.nameItem";

        List<Object> parameters = new ArrayList<>(query.whereParameters());
        return withConnection(connection -> {
            List<CatalogFact> facts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        facts.add(read(rows));
                    }
                }
            }
            return facts;
        });
    }

    private static CatalogFact read(ResultSet rows) throws SQLException {
        Date movedOn = rows.getDate("moved_on");
        // wasNull() reports on the most recent read, so each id is tested before the next
        // column is touched. Reading both and then asking twice would have the second
        // answer standing for the first.
        int mainGroup = rows.getInt("main_group_id");
        Integer mainGroupId = rows.wasNull() ? null : mainGroup;
        int subGroup = rows.getInt("sub_group_id");
        Integer subGroupId = rows.wasNull() ? null : subGroup;
        return new CatalogFact(
                rows.getInt("item_id"),
                rows.getString("barcode"),
                rows.getString("name_item"),
                // An item whose group row was deleted joins to nothing; the report says so
                // rather than dropping the item, which is what the grouped view on the items
                // screen used to do - the item simply vanished with nothing to explain it.
                mainGroupId,
                rows.getString("main_group_name"),
                subGroupId,
                rows.getString("sub_group_name"),
                rows.getString("unit_name"),
                rows.getDouble("buy_price"),
                rows.getDouble("sel_price1"),
                rows.getDouble("mini_quantity"),
                rows.getDouble("balance"),
                rows.getBoolean("item_active"),
                rows.getBoolean("item_has_validity"),
                movedOn == null ? null : movedOn.toLocalDate());
    }


    /**
     * What is left of every expiry batch, by the same arithmetic the invoice screen uses.
     * <p>
     * A batch grows when stock comes in on it - a purchase, a sales return - and shrinks
     * when stock goes out on it - a sale, a purchase return. That is
     * {@code JdbcInvoiceStockRepository.expiryBalancesSql} said over the whole catalogue
     * instead of over one document's items, and the two must not drift: a batch this report
     * calls empty is a batch the invoice screen would refuse to sell from.
     * <p>
     * Warehouses are not separated. The same box has one expiry date wherever it is
     * standing, and an owner reading this wants the whole exposure at once.
     * <p>
     * {@code HAVING} rather than a {@code WHERE} on the sum: a batch is only interesting
     * once everything that ever moved on it has been added up, and a batch that has been
     * sold out is not on a shelf.
     */
    @Override
    public List<ExpiringBatch> expiringBatches(ItemCatalogFilter filter) throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        String sql = """
                SELECT items.id                     AS item_id,
                       items.barcode                AS barcode,
                       items.nameItem               AS name_item,
                       items.buy_price              AS buy_price,
                       items.alert_days_before_expire AS alert_days,
                       sg.name                      AS sub_group_name,
                       mg.name_g                    AS main_group_name,
                       u.unit_name                  AS unit_name,
                       batches.expiration_date      AS expiration_date,
                       batches.remaining            AS remaining
                FROM items
                         JOIN %s ip ON items.id = ip.item_id
                         JOIN (SELECT item_id, expiration_date, SUM(base_quantity) AS remaining
                               FROM (%s) movements
                               GROUP BY item_id, expiration_date
                               HAVING SUM(base_quantity) > 0) batches
                              ON batches.item_id = items.id
                         LEFT JOIN sub_group sg ON sg.id = items.sub_num
                         LEFT JOIN main_group mg ON mg.id = sg.main_id
                         LEFT JOIN units u ON u.unit_id = items.unit_id
                %s
                ORDER BY batches.expiration_date, items.nameItem
                """.formatted(MOVEMENTS, BATCH_MOVEMENTS, query.where());

        List<Object> parameters = new ArrayList<>(query.whereParameters());
        return withConnection(connection -> {
            List<ExpiringBatch> batches = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.size(); index++) {
                    statement.setObject(index + 1, parameters.get(index));
                }
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Date expiry = rows.getDate("expiration_date");
                        if (expiry == null) continue;
                        String group = rows.getString("sub_group_name");
                        if (group == null) group = rows.getString("main_group_name");
                        batches.add(new ExpiringBatch(
                                rows.getInt("item_id"),
                                rows.getString("barcode"),
                                rows.getString("name_item"),
                                group == null ? "" : group,
                                rows.getString("unit_name"),
                                rows.getDouble("buy_price"),
                                expiry.toLocalDate(),
                                rows.getDouble("remaining"),
                                rows.getInt("alert_days")));
                    }
                }
            }
            return batches;
        });
    }

    /**
     * Everything that has ever moved on a dated batch, signed.
     * <p>
     * The four document families again, with the two that bring stock in adding and the two
     * that take it out subtracting - and the column called {@code num} on the invoices and
     * {@code item_id} on the returns, which is the spread {@code ItemReferenceRegistry}
     * exists to remember. A line with no expiry date is not part of a batch and is excluded
     * here rather than grouped under a null.
     */
    private static final String BATCH_MOVEMENTS = """
            SELECT p.num AS item_id, p.expiration_date, p.quantity * p.type_value AS base_quantity
              FROM purchase p WHERE p.expiration_date IS NOT NULL
            UNION ALL
            SELECT sr.item_id, sr.expiration_date, sr.quantity * sr.type_value
              FROM sales_re sr WHERE sr.expiration_date IS NOT NULL
            UNION ALL
            SELECT s.num, s.expiration_date, -(s.quantity * s.type_value)
              FROM sales s WHERE s.expiration_date IS NOT NULL
            UNION ALL
            SELECT pr.item_id, pr.expiration_date, -(pr.quantity * pr.type_value)
              FROM purchase_re pr WHERE pr.expiration_date IS NOT NULL
            """;

    /**
     * Borrows a connection for the length of one read and gives it straight back, the way
     * every {@code AbstractDao} helper does. A report joins whatever transaction happens to
     * be open on this thread, and opens none of its own - it writes nothing.
     */
    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not read the item catalogue for a report", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
