package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Area;
import com.hamza.account.party.PartyTableSpec;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.party.PartyWriteGuard;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.trial.TrialManager;
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

public class CustomerDao extends AbstractDao<Customers> {

    /** Where a customer lives, and every statement over it. */
    static final PartyTableSpec SPEC = PartyTableSpec.CUSTOMER;

    public static final String NAME = PartyTableSpec.NAME;
    private static final int FILTER_LIMIT = PartyTableSpec.SEARCH_LIMIT;
    private final String ID = "id";
    private final String TEL = "tel";
    private final String ADDRESS = "address";
    private final String NOTES = "notes";
    private final String LIMIT_NUM = "limit_num";
    private final String FIRST_BALANCE = "first_balance";
    /** Where the opening balance sits in the array {@link #getData} builds. */
    private static final int OPENING_BALANCE_INDEX = SPEC.openingBalanceIndex();
    /** The opening balance and its date, highest index first - see {@link PartyTableSpec#openingColumns()}. */
    private static final java.util.List<Integer> OPENING_INDEXES = SPEC.openingColumnIndexes();
    private final String ITEMS_SEL_PRICE_ID = "price_id";
    private final String EMAIL = "email";
    private final String TAX_NUMBER = "tax_number";
    private final String PAYMENT_TERMS = "payment_terms_days";
    private final String DEFAULT_DELEGATE = "default_delegate_id";
    private final String OPENING_DATE = "opening_balance_date";
    private final String IS_ACTIVE = "is_active";
    private final String TABLE = SPEC.table();
    private final String USER_ID = "user_id";
    private final String AREA_ID = "area_id";
    private final String AREA_NAME = "area_name";
    private final String DATE_INSERT = SPEC.createdColumn();
    private final DaoFactory daoFactory;

    CustomerDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
    }

    @Override
    public List<Customers> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    // ---- the statements ---------------------------------------------------------
    // Named so PartyDaoStatementsTest can read them without a database. The supplier
    // DAO is the same file with three columns missing; these are what differ.

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

    String selectByIdSql() {
        return SPEC.selectByIdSql();
    }

    String selectByNameSql() {
        return SPEC.selectByNameSql();
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
    public int insert(Customers model) throws DaoException {
        if (!withConnection(c -> new TrialManager(c).canAddCustomer())) return 0;
        Object[] objects = {model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getCredit_limit()
                , model.getFirst_balance()
                , openingDate(model)
                , model.getSelPriceObject().getId()
                , model.getUsers().getId()
                , model.getArea().getId()
                , model.getEmail()
                , model.getTax_number()
                , model.getPayment_terms_days()
                , model.getDefault_delegate_id()
                , model.isActive()
        };
        return executeUpdate(insertSql(), objects);
    }

    /**
     * Saves the customer.
     * <p>
     * <b>The opening balance is written only while the customer has never moved.</b> It
     * is the one figure on the row with no date on it - a statement is
     * {@code first_balance + invoices - payments} - so changing it changes what the
     * customer owed at every moment of their history, and a statement printed and
     * signed last month prints differently today. Once there is an invoice, a return or
     * a payment it is a closed entry, and the correction is a new dated movement on the
     * account. See {@link OpeningBalanceRegistry#CUSTOMERS}.
     */
    @Override
    public int update(Customers model) throws DaoException {
        boolean mayWriteOpening = OpeningBalanceGuard.shared()
                .mayWrite(OpeningBalanceRegistry.CUSTOMERS, model.getId(), model.getFirst_balance());

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
    public Customers getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
    }

    @Override
    public Customers getDataByString(String s) throws DaoException {
        return queryForObject(selectByNameSql(), this::map, s);
    }

    @Override
    public Object[] getData(Customers model) {
        return new Object[]{model.getName()
                , model.getTel()
                , model.getAddress()
                , model.getNotes()
                , model.getCredit_limit()
                , model.getFirst_balance()
                , openingDate(model)
                , model.getSelPriceObject().getId()
                , model.getArea().getId()
                , model.getEmail()
                , model.getTax_number()
                , model.getPayment_terms_days()
                , model.getDefault_delegate_id()
                , model.isActive()
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
            // DECIMAL(14, 2), and read with getInt until now: a limit of 5000.50 came
            // back as 5000, so the screen showed one number and saved another.
            customers.setCredit_limit(rs.getDouble(LIMIT_NUM));
            customers.setFirst_balance(rs.getDouble(FIRST_BALANCE));
            customers.setSelPriceObject(daoFactory.getItemsSelPriceDao().getDataById(rs.getInt(ITEMS_SEL_PRICE_ID)));
            // The area join is a LEFT join, so the name is absent for a customer whose
            // area row has been deleted. The id it still carries is what the area combo
            // needs; an empty name reads as "no area" rather than throwing.
            String areaName = rs.getString(AREA_NAME);
            customers.setArea(new Area(rs.getInt(AREA_ID), areaName == null ? "" : areaName));
            customers.setEmail(rs.getString(EMAIL));
            customers.setTax_number(rs.getString(TAX_NUMBER));
            customers.setPayment_terms_days(rs.getInt(PAYMENT_TERMS));
            customers.setDefault_delegate_id(rs.getInt(DEFAULT_DELEGATE));
            java.sql.Date openingDate = rs.getDate(OPENING_DATE);
            customers.setOpening_balance_date(openingDate == null ? null : openingDate.toLocalDate());
            customers.setActive(rs.getBoolean(IS_ACTIVE));
            customers.setCreated_at(rs.getTimestamp(DATE_INSERT).toLocalDateTime());
            customers.setUpdated_at(rs.getTimestamp("updated_at").toLocalDateTime());
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return customers;
    }

    /**
     * The three-phase search, narrowed to active parties or not.
     * <p>
     * The scope reaches SQL rather than filtering the answer, because each phase is
     * {@code LIMIT 50}: dropping stopped parties from a returned page would return fewer
     * than fifty and leave the ones below the cut unreachable by any amount of typing.
     */
    public List<Customers> getFilterCustomers(String searchText, PartySearchScope scope) throws DaoException {
        if (searchText == null || searchText.trim().isEmpty()) {
            return queryForObjects(filterAllSql(scope), this::map);
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

            return queryForObjects(filterNumericSql(scope), this::map, id, q, id, q);
        }

        // 2) نص/مختلط: مرحلتين startsWith ثم contains
        final String likeStarts = q + "%";
        final String likeContains = "%" + q + "%";

        Map<Integer, Customers> result = new java.util.LinkedHashMap<>(FILTER_LIMIT);

        // Phase A: startsWith (سريع)
        List<Customers> starts = queryForObjects(
                filterStartsSql(scope),
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
                    filterContainsSql(scope),
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
        return queryForObjects(pageSql(), this::map, rowsPerPage, offset);
    }

    public int getCountItems() {
        return queryForIntOrDefault(countSql(), 0);
    }

    /**
     * The opening-balance date as SQL sees it, or the day the row was entered.
     * <p>
     * Never null on the way in: V56 backfilled every existing row with
     * {@code DATE(created_at)} - which is the date the ledger already used, so no figure
     * moved - and a save that wrote null back would undo that for the one row it touched
     * while every other row kept a date.
     */
    private static Object openingDate(Customers model) {
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
                        throw new DaoException("Customer disappeared after update: " + id);
                    }
                    return resultSet.getTimestamp("updated_at").toLocalDateTime();
                }
            } catch (SQLException e) {
                throw new DaoException(e.getMessage(), e);
            }
        });
    }
}
