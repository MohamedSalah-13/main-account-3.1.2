package com.hamza.account.features.stockopening;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads the opening-balances screen's rows - {@link WarehouseOpeningQuery}'s statements, bound. */
public class WarehouseOpeningDao extends AbstractDao<WarehouseOpeningRow> {

    /** One page and one extra row, by name. */
    public List<WarehouseOpeningRow> page(WarehouseOpeningFilter filter) throws DaoException {
        Object[] where = WarehouseOpeningQuery.whereValues(filter);
        Object[] values = Arrays.copyOf(where, where.length + 2);
        values[where.length] = filter.queryLimit();
        values[where.length + 1] = filter.offset();
        return withConnection(connection -> {
            List<WarehouseOpeningRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(WarehouseOpeningQuery.PAGE)) {
                setData(statement, values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(map(rs));
                    }
                }
            }
            return rows;
        });
    }

    /** How many items the whole filter matches. */
    public long count(WarehouseOpeningFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(WarehouseOpeningQuery.COUNT)) {
                setData(statement, WarehouseOpeningQuery.whereValues(filter));
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    @Override
    public WarehouseOpeningRow map(ResultSet rs) throws DaoException {
        try {
            return new WarehouseOpeningRow(rs.getInt("id"), rs.getString("barcode"), rs.getString("nameItem"),
                    rs.getString("unit_name"), rs.getDouble("first_balance"), rs.getBoolean("moved"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
