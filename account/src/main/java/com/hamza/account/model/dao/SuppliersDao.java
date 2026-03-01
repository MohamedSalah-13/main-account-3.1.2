package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Suppliers;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SuppliersDao extends AbstractDao<Suppliers> {

    public static final String NAME = "name";
    private final String ID = "id";
    private final String TEL = "tel";
    private final String ADDRESS = "address";
    private final String NOTES = "notes";
    private final String FIRST_BALANCE = "first_balance";
    private final String TABLE_NAME = "suppliers";
    private final String USER_ID = "user_id";
    private final String AREA_ID = "area_id";
    private final String AREA_NAME = "area_name";
    private final String DATE_INSERT = "date_insert";
    private final DaoFactory daoFactory;

    SuppliersDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Suppliers> loadAll() throws DaoException {
        String query = "SELECT * FROM suppliers join table_area on suppliers.area_id = table_area.id";
        return queryForObjects(query, this::map);
    }

    @Override
    public int insert(Suppliers model) throws DaoException {
        Object[] objects = {model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getFirst_balance()
                , model.getUsers().getId()
                , model.getArea().getId()};

        String query = SqlStatements.insertStatement(TABLE_NAME, NAME, TEL, ADDRESS, NOTES, FIRST_BALANCE, USER_ID, AREA_ID);

        return executeUpdate(query, objects);
    }

    @Override
    public int update(Suppliers model) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE_NAME, ID, NAME, TEL, ADDRESS, NOTES, FIRST_BALANCE, AREA_ID);
        return executeUpdate(query, getData(model));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public Suppliers getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public Object[] getData(Suppliers model) {
        return new Object[]{model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getFirst_balance()
                , model.getArea().getId()
                , model.getId()};
    }

    @Override
    public Suppliers map(ResultSet resultSet) throws DaoException {
        Suppliers suppliers = new Suppliers();
        try {
            suppliers.setId(resultSet.getInt(ID));
            suppliers.setName(resultSet.getString(NAME));
            String tel = resultSet.getString(TEL);
            suppliers.setTel(tel == null ? "" : tel);

            String address = resultSet.getString(ADDRESS);
            suppliers.setAddress(address == null ? "" : address);

            String notes = resultSet.getString(NOTES);
            suppliers.setNotes(notes == null ? "" : notes);

            suppliers.setFirst_balance(resultSet.getDouble(FIRST_BALANCE));
//            suppliers.setArea(new Area(resultSet.getInt(AREA_ID), resultSet.getString(AREA_NAME)));
            suppliers.setArea(daoFactory.areaDao().getDataById(resultSet.getInt(AREA_ID)));
            suppliers.setCreated_at(LocalDateTime.parse(resultSet.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return suppliers;
    }

}
