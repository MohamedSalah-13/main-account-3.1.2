package com.hamza.account.features.report.itemsales;

import com.hamza.account.features.itemreports.JdbcItemSalesRepository;
import com.hamza.account.features.items.ItemCatalogSql;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only JDBC side of the item sales report. The invoices' own discounts are asked of the Pareto
 * reports' repository rather than written a second time: they are one figure, which turns the items'
 * total into the invoices' on both reports.
 */
public final class JdbcItemSalesReportRepository extends AbstractDao<ItemSalesRow> implements ItemSalesRepository {

    private final JdbcItemSalesRepository paretoSales = new JdbcItemSalesRepository();
    private final LinesDao linesDao = new LinesDao();

    @Override
    public List<ItemSalesRow> rows(ItemSalesFilter filter, boolean withCost) throws DaoException {
        ItemCatalogSql.Statement where = ItemCatalogSql.build(filter.catalogFilter());
        List<Object> parameters = new ArrayList<>(List.of(Date.valueOf(filter.from()), Date.valueOf(filter.to()),
                Date.valueOf(filter.from()), Date.valueOf(filter.to())));
        parameters.addAll(where.whereParameters());
        return queryForObjects(ItemSalesQuery.rowsSql(where, withCost), rs -> map(rs, withCost), parameters.toArray());
    }

    @Override
    public BigDecimal headerDiscounts(LocalDate from, LocalDate to) throws DaoException {
        return BigDecimal.valueOf(paretoSales.headerDiscounts(from, to));
    }

    @Override
    public List<ItemSalesLine> lines(int itemId, LocalDate from, LocalDate to) throws DaoException {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to);
        return linesDao.queryForObjects(ItemSalesQuery.linesSql(), linesDao::map,
                start, end, itemId, start, end, itemId);
    }

    @Override
    public ItemSalesRow map(ResultSet rs) throws DaoException {
        return map(rs, false);
    }

    private static ItemSalesRow map(ResultSet rs, boolean withCost) throws DaoException {
        try {
            String group = rs.getString("sub_group_name");
            if (group == null) {
                group = rs.getString("main_group_name");
            }
            return new ItemSalesRow(rs.getInt("item_id"), rs.getString("name_item"), group,
                    rs.getString("unit_name"), rs.getBigDecimal("sold_quantity"),
                    rs.getBigDecimal("returned_quantity"), rs.getInt("invoices"), rs.getBigDecimal("sold"),
                    rs.getBigDecimal("returned"), withCost ? orZero(rs.getBigDecimal("cost")) : null);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /** An item's lines are a second kind of row, and {@code AbstractDao} maps one kind. */
    private static final class LinesDao extends AbstractDao<ItemSalesLine> {

        @Override
        public ItemSalesLine map(ResultSet rs) throws DaoException {
            try {
                return new ItemSalesLine(rs.getInt("is_return") == 1, rs.getString("unit_name"),
                        rs.getBigDecimal("factor"), rs.getBigDecimal("price"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("discount"), rs.getBigDecimal("amount"), rs.getInt("documents"));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
