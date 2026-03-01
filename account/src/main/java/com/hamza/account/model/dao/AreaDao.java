package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Area;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class AreaDao extends AbstractDao<Area> {

    private final String TABLE_NAME = "table_area";
    private final String ID = "id";
    private final String NAME = "area_name";

    public AreaDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Area> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(Area area) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, NAME), area.getArea_name());
    }

    @Override
    public int update(Area area) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, NAME), area.getArea_name(), area.getId());
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Area getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Area map(ResultSet rs) throws DaoException {
        Area area;
        try {
            area = new Area();
            area.setId(rs.getInt(ID));
            area.setArea_name(rs.getString(NAME));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return area;
    }
}
