package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.trial.TrialManager;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerDao extends AbstractDao<Customers> {

    public static final String NAME = "name";
    private final String ID = "id";
    private final String TEL = "tel";
    private final String ADDRESS = "address";
    private final String NOTES = "notes";
    private final String LIMIT_NUM = "limit_num";
    private final String FIRST_BALANCE = "first_balance";
    private final String ITEMS_SEL_PRICE_ID = "price_id";
    private final String TABLE = "custom";
    private final String USER_ID = "user_id";
    private final String AREA_ID = "area_id";
    private final String AREA_NAME = "area_name";
    private final String DATE_INSERT = "created_at";

    private final DaoFactory daoFactory;

    CustomerDao(Connection connection, DaoFactory daoFactory) {
        super(connection);
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Customers> loadAll() throws DaoException {
        String query = "SELECT * FROM custom" +
                " INNER JOIN table_area ON custom.area_id = table_area.id";
        return queryForObjects(query, this::map);
    }

    @Override
    public int insert(Customers model) throws DaoException {
        if (!new TrialManager(connection).canAddCustomer()) return 0;
        Object[] objects = {model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getCredit_limit()
                , model.getFirst_balance()
                , model.getSelPriceObject().getId()
                , model.getUsers().getId()
                , model.getArea().getId()
        };
        return executeUpdate(SqlStatements.insertStatement(TABLE, NAME, TEL, ADDRESS, NOTES, LIMIT_NUM, FIRST_BALANCE, ITEMS_SEL_PRICE_ID, USER_ID, AREA_ID), objects);
    }

    @Override
    public int update(Customers model) throws DaoException {
        String query = SqlStatements.updateStatement(TABLE, ID, NAME, TEL, ADDRESS, NOTES, LIMIT_NUM, FIRST_BALANCE, ITEMS_SEL_PRICE_ID, AREA_ID);
        return executeUpdate(query, getData(model));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE, ID), id);
    }

    @Override
    public Customers getDataById(int id) throws DaoException {
        String query = "SELECT * FROM custom" +
                " INNER JOIN table_area ON custom.area_id = table_area.id where custom.id = ?";
        return queryForObject(query, this::map, id);
    }

    @Override
    public Customers getDataByString(String s) throws DaoException {
        String query = "SELECT * FROM custom" +
                " INNER JOIN table_area ON custom.area_id = table_area.id where custom.name = ?";
        return queryForObject(query, this::map, s);
    }

    @Override
    public Object[] getData(Customers model) {
        return new Object[]{model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getCredit_limit()
                , model.getFirst_balance()
                , model.getSelPriceObject().getId()
                , model.getArea().getId()
                , model.getId()};
    }

    @Override
    public Customers map(ResultSet rs) throws DaoException {
        Customers customers = new Customers();
        try {
            customers.setId(rs.getInt(ID));
            customers.setName(rs.getString(NAME));
            String tel = rs.getString(TEL);
            customers.setTel(tel == null ? "" : tel);
            String address = rs.getString(ADDRESS);
            customers.setAddress(address == null ? "" : address);
            String notes = rs.getString(NOTES);
            customers.setNotes(notes == null ? "" : notes);
            customers.setCredit_limit(rs.getInt(LIMIT_NUM));
            customers.setFirst_balance(rs.getDouble(FIRST_BALANCE));
            customers.setSelPriceObject(daoFactory.getItemsSelPriceDao().getDataById(rs.getInt(ITEMS_SEL_PRICE_ID)));
            customers.setArea(new Area(rs.getInt(AREA_ID), rs.getString(AREA_NAME)));
            customers.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return customers;
    }
}
