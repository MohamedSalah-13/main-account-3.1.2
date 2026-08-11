package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.party.PartyTableSpec;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SuppliersDao extends AbstractDao<Suppliers> {

    /** Where a supplier lives, and every statement over it. */
    static final PartyTableSpec SPEC = PartyTableSpec.SUPPLIER;

    public static final String NAME = PartyTableSpec.NAME;
    private static final int FILTER_LIMIT = PartyTableSpec.SEARCH_LIMIT;
    private final String ID = "id";
    private final String TEL = "tel";
    private final String ADDRESS = "address";
    private final String NOTES = "notes";
    private final String FIRST_BALANCE = "first_balance";
    /** Where the opening balance sits in the array {@link #getData} builds. */
    private static final int OPENING_BALANCE_INDEX = SPEC.openingBalanceIndex();
    private final String TABLE_NAME = SPEC.table();
    private final String USER_ID = "user_id";
    private final String AREA_ID = "area_id";
    private final String AREA_NAME = "area_name";
    private final String DATE_INSERT = "date_insert";
    private final DaoFactory daoFactory;

    SuppliersDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Suppliers> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    // ---- the statements ---------------------------------------------------------
    // Named so PartyDaoStatementsTest can read them without a database. See CustomerDao:
    // the same statements, less the credit limit and the price tier a supplier has no
    // use for.

    String selectAllSql() {
        return SPEC.selectAllSql();
    }

    String insertSql() {
        return SPEC.insertSql();
    }

    String updateSql() {
        return SPEC.updateSql();
    }

    /** The same update with the opening balance left out - see {@link #update}. */
    String updateWithoutOpeningSql() {
        return SPEC.updateWithoutOpeningSql();
    }

    String deleteSql() {
        return SPEC.deleteSql();
    }

    /** No area join, unlike the customer's: {@link #map} reads the area with its own query. */
    String selectByIdSql() {
        return SPEC.selectByIdSql();
    }

    String filterAllSql() {
        return SPEC.searchAllSql();
    }

    String filterNumericSql() {
        return SPEC.searchByNumberSql();
    }

    String filterStartsSql() {
        return SPEC.searchByPrefixSql();
    }

    String filterContainsSql() {
        return SPEC.searchByFragmentSql();
    }

    String pageSql() {
        return SPEC.pageSql();
    }

    String countSql() {
        return SPEC.countSql();
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

        return executeUpdate(insertSql(), objects);
    }

    /**
     * Saves the supplier.
     * <p>
     * <b>The opening balance is written only while the supplier has never moved</b> -
     * the same rule, and the same reason, as {@code CustomerDao.update}: a statement is
     * {@code first_balance + invoices - payments}, so editing it rewrites what was owed
     * at every earlier date. See {@link OpeningBalanceRegistry#SUPPLIERS}.
     */
    @Override
    public int update(Suppliers model) throws DaoException {
        boolean mayWriteOpening = OpeningBalanceGuard.shared()
                .mayWrite(OpeningBalanceRegistry.SUPPLIERS, model.getId(), model.getFirst_balance());

        if (mayWriteOpening) {
            return executeUpdate(updateSql(), getData(model));
        }

        return executeUpdate(updateWithoutOpeningSql(), OpeningBalanceGuard.without(getData(model), OPENING_BALANCE_INDEX));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Suppliers getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
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

    public List<Suppliers> getFilterSuppliers(String searchText) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects(filterAllSql(), this::map);
        }

        String q = searchText.trim();
        boolean numericOnly = q.matches("\\d+");

        // 1) لو أرقام فقط: بحث سريع (id أو رقم التليفون)
        if (numericOnly) {
            int id = -1;
            try {
                id = Integer.parseInt(q);
            } catch (NumberFormatException ignored) {
            }

            return queryForObjects(filterNumericSql(), this::map, id, q, id, q);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Suppliers> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        // Phase A: startsWith
        List<Suppliers> starts = queryForObjects(
                filterStartsSql(),
                this::map,
                likeStarts, likeStarts, // WHERE
                likeStarts, likeStarts  // ORDER BY
        );

        for (Suppliers s : starts) {
            if (s != null) result.putIfAbsent(s.getId(), s);
        }

        // Phase B: contains
        if (result.size() < FILTER_LIMIT) {
            List<Suppliers> contains = queryForObjects(
                    filterContainsSql(),
                    this::map,
                    likeContains, likeContains // WHERE
            );
            for (Suppliers s : contains) {
                if (s != null) result.putIfAbsent(s.getId(), s);
                if (result.size() >= FILTER_LIMIT) break;
            }
        }

        return new java.util.ArrayList<>(result.values());
    }

    public List<Suppliers> getProducts(int rowsPerPage, int offset) throws DaoException {
        return queryForObjects(pageSql(), this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForIntOrDefault(countSql(), 0);
    }

}
