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
import java.util.Map;

public class CustomerDao extends AbstractDao<Customers> {

    public static final String NAME = "name";
    private static final int FILTER_LIMIT = 50;
    private static final String FILTER_CUSTOMERS_SQL_NUMERIC = """
            SELECT * FROM custom
            INNER JOIN table_area ON custom.area_id = table_area.id
            WHERE custom.id = ? OR custom.tel = ?
            ORDER BY
                CASE
                    WHEN custom.id = ? THEN 0
                    WHEN custom.tel = ? THEN 1
                    ELSE 2
                END,
                custom.id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_CUSTOMERS_SQL_TEXT_STARTS = """
            SELECT * FROM custom
            INNER JOIN table_area ON custom.area_id = table_area.id
            WHERE custom.name LIKE ? OR custom.tel LIKE ?
            ORDER BY
                CASE
                    WHEN custom.name LIKE ? THEN 0
                    WHEN custom.tel LIKE ? THEN 1
                    ELSE 2
                END,
                custom.id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
    private static final String FILTER_CUSTOMERS_SQL_TEXT_CONTAINS = """
            SELECT * FROM custom
            INNER JOIN table_area ON custom.area_id = table_area.id
            WHERE custom.name LIKE ? OR custom.tel LIKE ?
            ORDER BY custom.id DESC
            LIMIT %d
            """.formatted(FILTER_LIMIT);
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

    public List<Customers> getFilterCustomers(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id ORDER BY custom.id DESC LIMIT " + FILTER_LIMIT, this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        // 1) لو أرقام فقط: بحث سريع ودقيق (id أو رقم التليفون)
        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            } // في حال كان رقم الهاتف طويلاً جداً

            return queryForObjects(FILTER_CUSTOMERS_SQL_NUMERIC, this::map, id, q, id, q);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Customers> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        // Phase A: startsWith (سريع)
        List<Customers> starts = queryForObjects(
                FILTER_CUSTOMERS_SQL_TEXT_STARTS,
                this::map,
                likeStarts, likeStarts, // WHERE
                likeStarts, likeStarts  // ORDER BY
        );

        for (Customers c : starts) {
            if (c != null) result.putIfAbsent(c.getId(), c);
        }

        // Phase B: contains (%text%) فقط إذا لم نصل للحد الأقصى
        if (result.size() < FILTER_LIMIT) {
            List<Customers> contains = queryForObjects(
                    FILTER_CUSTOMERS_SQL_TEXT_CONTAINS,
                    this::map,
                    likeContains, likeContains // WHERE
            );
            for (Customers c : contains) {
                if (c != null) result.putIfAbsent(c.getId(), c);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<Customers> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects("SELECT * FROM custom INNER JOIN table_area ON custom.area_id = table_area.id ORDER BY custom.id DESC LIMIT ? OFFSET ?", this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForInt("SELECT COUNT(*) FROM custom");
    }
}
