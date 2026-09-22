package com.hamza.account.features.itemreports;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads what each item sold over a period, for the Pareto reports.
 *
 * <p>Built from {@link DocumentTableSpec} and {@link ItemNetLines#lineAmount} so a line's amount is
 * the one every other item report uses, and each side - the sales and their returns - is grouped
 * before the union, the rule that keeps an invoice from being multiplied by its returns. The filter's
 * {@code WHERE} is {@link ItemCatalogSql}'s, the items screen's own, and the movement row is joined
 * only when a balance condition asks for it ({@link ItemCatalogSql#requiresMovementJoin}): it
 * aggregates the whole catalogue and a sales ranking does not need a single balance out of it.</p>
 */
public final class JdbcItemSalesRepository implements ItemSalesRepository {

    /** The sales' own discounts less the returns', each over its own date column. Two parameters each. */
    static final String HEADER_DISCOUNTS = """
            SELECT (SELECT COALESCE(SUM(h.discount), 0) FROM %1$s h WHERE h.%2$s BETWEEN ? AND ?)
                 - (SELECT COALESCE(SUM(r.discount), 0) FROM %3$s r WHERE r.%4$s BETWEEN ? AND ?) AS header_discounts"""
            .formatted(DocumentTableSpec.SALES.table(), DocumentTableSpec.SALES.dateColumn(),
                    DocumentTableSpec.SALES_RETURN.table(), DocumentTableSpec.SALES_RETURN.dateColumn());
    static final int HEADER_DISCOUNTS_PARAMETERS = 4;
    /** The period, for the sales and again for the returns - before the filter's own parameters. */
    static final int PERIOD_PARAMETERS = 4;

    @Override
    public List<ItemSalesFact> sales(ItemCatalogFilter filter, LocalDate from, LocalDate to) throws DaoException {
        ItemCatalogSql.Statement query = ItemCatalogSql.build(filter);
        String sql = salesSql(query, ItemCatalogSql.requiresMovementJoin(filter));
        List<Object> parameters = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to),
                Date.valueOf(from), Date.valueOf(to)));
        parameters.addAll(query.whereParameters());
        return withConnection(connection -> {
            List<ItemSalesFact> facts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String group = rows.getString("sub_group_name");
                        if (group == null) {
                            group = rows.getString("main_group_name");
                        }
                        facts.add(new ItemSalesFact(rows.getInt("item_id"), rows.getString("name_item"),
                                group,
                                rows.getString("unit_name"), rows.getDouble("quantity"), rows.getDouble("net"),
                                rows.getDouble("cost")));
                    }
                }
            }
            return facts;
        });
    }

    @Override
    public double headerDiscounts(LocalDate from, LocalDate to) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(HEADER_DISCOUNTS)) {
                bind(statement, List.of(Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to)));
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? rows.getDouble("header_discounts") : 0;
                }
            }
        });
    }

    /**
     * The statement {@link #sales} runs: the period's lines grouped per item on each side, joined to
     * the item and narrowed by the filter. Package-private so its shape is pinned without a database.
     */
    static String salesSql(ItemCatalogSql.Statement query, boolean movementJoin) {
        return """
                SELECT items.id AS item_id,
                       items.nameItem AS name_item,
                       sg.name AS sub_group_name,
                       mg.name_g AS main_group_name,
                       u.unit_name AS unit_name,
                       s.quantity AS quantity,
                       s.net AS net,
                       s.cost AS cost
                FROM (SELECT m.item_id, SUM(m.quantity) AS quantity, SUM(m.net) AS net, SUM(m.cost) AS cost
                      FROM (%s
                            UNION ALL
                            %s) m
                      GROUP BY m.item_id) s
                         JOIN items ON items.id = s.item_id
                """.formatted(side(DocumentTableSpec.SALES, "d", "h", false),
                side(DocumentTableSpec.SALES_RETURN, "r", "rh", true))
                + (movementJoin ? "         JOIN " + ItemCatalogSql.MOVEMENTS + " ip ON items.id = ip.item_id\n" : "")
                + """
                         LEFT JOIN sub_group sg ON sg.id = items.sub_num
                         LEFT JOIN main_group mg ON mg.id = sg.main_id
                         LEFT JOIN units u ON u.unit_id = items.unit_id
                """
                + query.where();
    }

    /** One side's lines, grouped per item; a return's figures are negative so the two add up. */
    private static String side(DocumentTableSpec spec, String line, String header, boolean isReturn) {
        String sign = isReturn ? "-" : "";
        return ("SELECT %1$s.%2$s AS item_id, %3$sSUM(%1$s.quantity * %1$s.type_value) AS quantity, "
                + "%3$sSUM(%4$s) AS net, %3$sSUM(%1$s.total_buy_price) AS cost "
                + "FROM %5$s %1$s JOIN %6$s %7$s ON %7$s.%8$s = %1$s.%9$s "
                + "WHERE %7$s.%10$s BETWEEN ? AND ? GROUP BY %1$s.%2$s")
                .formatted(line, spec.lineItem(), sign, ItemNetLines.lineAmount(spec, line), spec.lineTable(),
                        spec.table(), header, spec.key(), DocumentTableSpec.LINE_DOCUMENT, spec.dateColumn());
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    /** Borrows a connection for one read and gives it straight back, as {@code JdbcCatalogFactRepository} does. */
    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not read the items' sales for a report", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
