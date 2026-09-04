package com.hamza.account.model.dao;

import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubGroupsDao extends AbstractDao<SubGroups> {

    private final String ID = "id";
    private final String NAME = "name";
    private final String TABLE_NAME = "sub_group";
    private final String MAIN_ID = "main_id";
    private final String USER_ID = "user_id";
    private final DaoFactory daoFactory;

    SubGroupsDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<SubGroups> loadAll() throws DaoException {
        String query = SqlStatements.selectStatement(TABLE_NAME);
        return queryForObjects(query, this::map);
    }

    /**
     * Every sub group with its main group already attached, resolved from a single
     * pass over {@code main_group} rather than a lookup per row.
     * <p>
     * {@link #map} asks {@code MainGroupsDao} for the parent of every row it maps,
     * which is one query per sub group. That is affordable for a screen loading this
     * list once; it is not affordable underneath a catalog mapper, where it multiplies
     * by the number of items on the page. Two queries, whatever the size of the list.
     */
    public List<SubGroups> loadAllResolved() throws DaoException {
        Map<Integer, MainGroups> mainGroups = new HashMap<>();
        for (MainGroups group : daoFactory.getMainGroups().loadAll()) {
            mainGroups.put(group.getId(), group);
        }
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), resultSet -> {
            try {
                SubGroups subGroups = new SubGroups();
                subGroups.setId(resultSet.getInt(ID));
                subGroups.setName(resultSet.getString(NAME));
                subGroups.setMainGroups(mainGroups.get(resultSet.getInt(MAIN_ID)));
                return subGroups;
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        });
    }

    @Override
    public List<SubGroups> loadAllById(int id) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID);
        return queryForObjects(query, this::map, id);
    }

    @Override
    public int insert(SubGroups mainGroupModel) throws DaoException {
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, NAME, MAIN_ID, USER_ID)
                , mainGroupModel.getName(), mainGroupModel.getMainGroups().getId(), mainGroupModel.getUsers().getId());
    }

    @Override
    public int update(SubGroups mainGroups) throws DaoException {
        String update = SqlStatements.updateStatement(TABLE_NAME, ID, NAME, MAIN_ID);
        return executeUpdate(update, getData(mainGroups));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        if (id <= 0)
            throw new IllegalArgumentException("Invalid sub group ID: " + id);
        if (id == 1)
            throw new IllegalArgumentException("Cannot delete sub group with ID 1");
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public SubGroups getDataById(int id) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID);
        return queryForObject(query, this::map, id);
    }

    @Override
    public SubGroups getDataByString(String s) throws DaoException {
        String query = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, NAME);
        return queryForObject(query, this::map, s);
    }

    @Override
    public Object[] getData(SubGroups mainGroups) {
        return new Object[]{mainGroups.getName(), mainGroups.getMainGroups().getId(), mainGroups.getId()};
    }

    @Override
    public SubGroups map(ResultSet resultSet) throws DaoException {
        SubGroups subGroups = new SubGroups();
        try {
            subGroups.setId(resultSet.getInt(ID));
            subGroups.setName(resultSet.getString(NAME));
            int main_id = resultSet.getInt(MAIN_ID);
            MainGroups dataById = daoFactory.getMainGroups().getDataById(main_id);
            subGroups.setMainGroups(dataById);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return subGroups;
    }

    public SubGroups getDataByNameAndMainId(String name, int id) throws DaoException {
        String query = "select * from " + TABLE_NAME + " join main_group mg on sub_group." + MAIN_ID + " = mg." + ID + " where " + NAME + "=? and " + MAIN_ID + "=? ";
        return queryForObject(query, this::map, name, id);
    }
}
