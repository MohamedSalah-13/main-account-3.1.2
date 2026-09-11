package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.party.PartyTableSpec;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.party.PartyWriteGuard;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.opening.OpeningBalanceGuard;
import com.hamza.account.opening.OpeningBalanceRegistry;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
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
    private final String EMAIL = "email";
    private final String TAX_NUMBER = "tax_number";
    private final String PAYMENT_TERMS = "payment_terms_days";
    private final String OPENING_DATE = "opening_balance_date";
    private final String IS_ACTIVE = "is_active";
    /** Where the opening balance sits in the array {@link #getData} builds. */
    private static final int OPENING_BALANCE_INDEX = SPEC.openingBalanceIndex();
    /** The opening balance and its date, highest index first - see {@link PartyTableSpec#openingColumns()}. */
    private static final java.util.List<Integer> OPENING_INDEXES = SPEC.openingColumnIndexes();
    private final String TABLE_NAME = SPEC.table();
    private final String USER_ID = "user_id";
    private final String AREA_ID = "area_id";
    private final String AREA_NAME = "area_name";
    private final String DATE_INSERT = SPEC.createdColumn();
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

    String filterAllSql(PartySearchScope scope) {
        return SPEC.searchAllSql(scope);
    }

    String filterNumericSql(PartySearchScope scope) {
        return SPEC.searchByNumberSql(scope);
    }

    String filterStartsSql(PartySearchScope scope) {
        return SPEC.searchByPrefixSql(scope);
    }

    String filterContainsSql(PartySearchScope scope) {
        return SPEC.searchByFragmentSql(scope);
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
                , openingDate(model)
                , model.getUsers().getId()
                , model.getArea().getId()
                , model.getEmail()
                , model.getTax_number()
                , model.getPayment_terms_days()
                , model.isActive()};

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

        Object[] values = mayWriteOpening
                ? getData(model)
                : OpeningBalanceGuard.withoutAll(getData(model), OPENING_INDEXES);
        int affected = executeUpdate(mayWriteOpening ? updateSql() : updateWithoutOpeningSql(),
                PartyWriteGuard.withVersion(values, model.getUpdated_at()));
        PartyWriteGuard.requireUpdated(affected);
        model.setUpdated_at(readUpdatedAt(model.getId()));
        return affected;
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
                , openingDate(model)
                , model.getArea().getId()
                , model.getEmail()
                , model.getTax_number()
                , model.getPayment_terms_days()
                , model.isActive()
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
            suppliers.setEmail(resultSet.getString(EMAIL));
            suppliers.setTax_number(resultSet.getString(TAX_NUMBER));
            suppliers.setPayment_terms_days(resultSet.getInt(PAYMENT_TERMS));
            java.sql.Date openingDate = resultSet.getDate(OPENING_DATE);
            suppliers.setOpening_balance_date(openingDate == null ? null : openingDate.toLocalDate());
            suppliers.setActive(resultSet.getBoolean(IS_ACTIVE));
            suppliers.setCreated_at(resultSet.getTimestamp(DATE_INSERT).toLocalDateTime());
            suppliers.setUpdated_at(resultSet.getTimestamp("updated_at").toLocalDateTime());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return suppliers;
    }

    /**
     * The three-phase search, narrowed to active parties or not.
     * <p>
     * The scope reaches SQL rather than filtering the answer, because each phase is
     * {@code LIMIT 50}: dropping stopped parties from a returned page would return fewer
     * than fifty and leave the ones below the cut unreachable by any amount of typing.
     */
    public List<Suppliers> getFilterSuppliers(String searchText, PartySearchScope scope) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects(filterAllSql(scope), this::map);
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

            return queryForObjects(filterNumericSql(scope), this::map, id, q, id, q);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Suppliers> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        // Phase A: startsWith
        List<Suppliers> starts = queryForObjects(
                filterStartsSql(scope),
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
                    filterContainsSql(scope),
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

    /** As {@code CustomerDao.openingDate}: never null on the way in, since V56 backfilled every row. */
    private static Object openingDate(Suppliers model) {
        java.time.LocalDate date = model.getOpening_balance_date();
        if (date == null) {
            date = model.getCreated_at() == null
                    ? java.time.LocalDate.now() : model.getCreated_at().toLocalDate();
        }
        return java.sql.Date.valueOf(date);
    }

    private LocalDateTime readUpdatedAt(int id) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SPEC.updatedAtSql())) {
                statement.setInt(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new DaoException("Supplier disappeared after update: " + id);
                    }
                    return resultSet.getTimestamp("updated_at").toLocalDateTime();
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }

}
