package com.hamza.account.model.dao;

import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class UnitsDao extends AbstractDao<UnitsModel> {

    /** The unit the schema falls back to: {@code type INT DEFAULT 1}. */
    public static final int DEFAULT_UNIT_ID = 1;
    private final String TABLE_NAME = "units";
    private final String UNIT_ID = "unit_id";
    private final String UNIT_NAME = "unit_name";
    private final String VALUE_D = "value_d";
    private final String USER_ID = "user_id";

    protected UnitsDao() {
        super();
    }

    @Override
    public List<UnitsModel> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(UnitsModel unitsModel) throws DaoException {
        Object[] objects = {unitsModel.getUnit_name(), unitsModel.getValue(), unitsModel.getUsers().getId()};
        String insert = SqlStatements.insertStatement(TABLE_NAME, UNIT_NAME, VALUE_D, USER_ID);
        return executeUpdate(insert, objects);
    }

    @Override
    public int update(UnitsModel unitsModel) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, UNIT_ID, UNIT_NAME, VALUE_D);
        return executeUpdate(update, getData(unitsModel));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid unit ID: " + id);
        // Unit 1 is the DEFAULT on the type column of all four invoice tables and
        // on stock_movements, so a row written without a unit still resolves.
        // The rest are deletable when nothing points at them - see isInUse.
        if (id == DEFAULT_UNIT_ID)
            throw new DaoException("لا يمكن حذف الوحدة الافتراضية");
        if (isInUse(id))
            throw new DaoException("لا يمكن حذف وحدة مستخدمة في أصناف أو فواتير");
        String deleteStatement = SqlStatements.deleteStatement(TABLE_NAME, UNIT_ID);
        return executeUpdate(deleteStatement, id);
    }

    /**
     * Whether any item, item unit or saved invoice line still references this
     * unit. Checked before deleting rather than left to the foreign keys, so the
     * screen can say why instead of surfacing a constraint name.
     */
    public boolean isInUse(int id) throws DaoException {
        String query = """
                SELECT EXISTS (
                    SELECT 1 FROM items       WHERE unit_id = ?
                    UNION ALL
                    SELECT 1 FROM items_units WHERE unit    = ?
                    UNION ALL
                    SELECT 1 FROM purchase    WHERE type    = ?
                    UNION ALL
                    SELECT 1 FROM purchase_re WHERE type    = ?
                    UNION ALL
                    SELECT 1 FROM sales       WHERE type    = ?
                    UNION ALL
                    SELECT 1 FROM sales_re    WHERE type    = ?
                )
                """;
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (int i = 1; i <= 6; i++) {
                    statement.setInt(i, id);
                }
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

    @Override
    public UnitsModel getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, UNIT_ID), this::map, id);
    }

    @Override
    public UnitsModel getDataByString(String s) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, UNIT_NAME), this::map, s);
    }

    @Override
    public Object[] getData(UnitsModel unitsModel) throws DaoException {
        return new Object[]{unitsModel.getUnit_name(), unitsModel.getValue(), unitsModel.getUnit_id()};
    }

    @Override
    public UnitsModel map(ResultSet rs) throws DaoException {
        UnitsModel unitsModel = new UnitsModel();
        try {
            unitsModel.setUnit_id(rs.getInt(UNIT_ID));
            unitsModel.setUnit_name(rs.getString(UNIT_NAME));
            unitsModel.setValue(rs.getDouble(VALUE_D));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return unitsModel;
    }
}
