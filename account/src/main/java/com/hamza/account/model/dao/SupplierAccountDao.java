package com.hamza.account.model.dao;

import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.TableName;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.GenericMapper;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SupplierAccountDao extends AbstractDao<SupplierAccount> {

    /** Where a supplier's payments live, and every statement over them. */
    static final PartyLedgerSpec SPEC = PartyLedgerSpec.SUPPLIER;

    /** Kept public: the supplier statement screen builds on it. */
    public static final String SELECT_ACCOUNT_AND_TOTALS_BY_ID = SPEC.statementSql();
    private final String PURCHASE = "purchase";
    private final String INFORMATION = "information";
    //    private final String INFORMATION_DATA = "arabic_name";
    private final String DISCOUNT = "discount";
    private final String ACCOUNT_CODE = "account_code";
    private final String ACCOUNT_NUM = "account_num";
    private final String ACCOUNT_DATE = "account_date";
    private final String PAID = "paid";
    private final String NOTES = "notes";
    private final String NUMBER_INV = "numberInv";
    private final String TABLE_NAME = "suppliers_accounts";
    private final String TABLE_VIEW = "account_suppliers_table";
    private final String TABLE_VIEW_TOTALS = "account_suppliers_totals";
    private final String AMOUNT = "amount";
    private final String TREASURY_ID = "treasury_id";
    private final String DATE_INSERT = SPEC.createdColumn();
    private final String USER_ID = "user_id";
    private final String NAME = "name";

    SupplierAccountDao() {
        super();
    }

    @Override
    public List<SupplierAccount> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<SupplierAccount> loadAllById(int id) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, id);
    }

    // ---- the statements ---------------------------------------------------------
    // Named so PartyLedgerStatementsTest can read them without a database. See
    // CustomerAccountDao: the same ledger under other names.

    String statementSql() {
        return SPEC.statementSql();
    }

    String selectAllSql() {
        return SPEC.selectAllSql();
    }

    String selectByPartySql() {
        return SPEC.selectByPartySql();
    }

    String insertSql() {
        return SPEC.insertSql();
    }

    String updateSql() {
        return SPEC.updateSql();
    }

    String deleteSql() {
        return SPEC.deleteSql();
    }

    String selectForUpdateSql() {
        return SPEC.selectForUpdateSql();
    }

    String selectByPartyCodeSql() {
        return SPEC.selectByPartyCodeSql();
    }

    String totalsSql() {
        return SPEC.totalsSql();
    }

    String totalsBetweenDatesSql() {
        return SPEC.totalsBetweenDatesSql();
    }

    String betweenDatesSql() {
        return SPEC.betweenDatesSql();
    }

    @Override
    public int insert(SupplierAccount model) throws DaoException {
        // See CustomerAccountDao.insert: only payments are stored here, and the invoice
        // side of a statement comes from total_buy through account_suppliers_table.
        PeriodLock.require(model.getDate(), PeriodLockRegistry.SUPPLIER_ACCOUNT.label());
        return executeUpdate(insertSql(), getData(model));
    }

    @Override
    public int update(SupplierAccount supplierAccount) throws DaoException {
        PeriodLock.requireMove(PeriodLockRegistry.SUPPLIER_ACCOUNT, supplierAccount.getId(), supplierAccount.getDate());
        return executeUpdate(updateSql(), supplierAccount.getSuppliers().getId()
                , supplierAccount.getDate()
                , supplierAccount.getPaid()
                , supplierAccount.getNotes()
                , supplierAccount.getInvoice_number()
                , supplierAccount.getTreasury().getId()
                , supplierAccount.getId());
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }


    @Override
    public Object[] getData(SupplierAccount supplierAccount) {
        return new Object[]{supplierAccount.getSuppliers().getId()
                , supplierAccount.getDate()
                , supplierAccount.getPaid()
                , supplierAccount.getNotes()
                , supplierAccount.getInvoice_number()
                , supplierAccount.getTreasury().getId()
                , supplierAccount.getId()
                , supplierAccount.getUsers().getId()
        };
    }

    @Override
    public SupplierAccount map(ResultSet rs) throws DaoException {
        return createSupplierAccount(rs, true);
    }

    public SupplierAccount mapMain(ResultSet rs) throws DaoException {
        return createSupplierAccount(rs, false);
    }

    private SupplierAccount createSupplierAccount(ResultSet rs, boolean adjustPurchase) throws DaoException {
        SupplierAccount model;
        try {
            model = new SupplierAccount();
            double purchase = rs.getDouble(PURCHASE);
            double discount = adjustPurchase ? rs.getDouble(DISCOUNT) : 0;
            int codeSup = rs.getInt(ACCOUNT_CODE);
            model.setId(rs.getInt(ACCOUNT_NUM));
            model.setDate(rs.getString(ACCOUNT_DATE));
            model.setPurchase(purchase - discount);
            model.setPaid(rs.getDouble(PAID));
            model.setInvoice_number(rs.getInt(NUMBER_INV));
            model.setNotes(rs.getString(NOTES));
            model.setTreasury(new Treasury(rs.getInt(TREASURY_ID)));
            // See CustomerAccountDao: a row from the invoice half of the view has no
            // entry timestamp, and a missing one is not a reason to fail the listing.
            String createdAt = rs.getString(DATE_INSERT);
            if (createdAt != null) {
                model.setCreated_at(LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            String nameSup = adjustPurchase ? rs.getString(NAME) : "";
            model.setSuppliers(new Suppliers(codeSup, nameSup));
            if (adjustPurchase) {
                var tableNameById = TableName.requireById(rs.getInt(INFORMATION));
                model.setInformation(tableNameById);
                model.setInformation_name(tableNameById.getType());
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }

    /**
     * Retrieves a SupplierAccount based on the given account number.
     *
     * @param id the account number of the SupplierAccount to retrieve
     * @return the SupplierAccount if found
     * @throws DaoException if there is an error during the data access operation
     */
    public SupplierAccount getAccountByNum(int id) throws DaoException {
        String selectAccountById = selectForUpdateSql();
        GenericMapper<SupplierAccount> mapMain = resultSet -> {
            SupplierAccount map = mapMain(resultSet);
            map.getTreasury().setName(resultSet.getString("t_name"));
            return map;
        };
        return queryForObject(selectAccountById, mapMain, id);
    }

    public List<SupplierAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        return queryForObjects(selectByPartyCodeSql(), this::mapMain, accountCode);
    }

    /**
     * The summary of every supplier's account, over the whole history.
     *
     * @return a list of SupplierAccount objects containing the account summaries and totals for each supplier.
     * @throws DaoException if there is an error during the data access operation.
     */
    public List<SupplierAccount> getTotalsAccount() throws DaoException {
        return getTotalsAccount(null, null);
    }

    /**
     * The same summary over one period. The customer's ledger has answered this since it
     * was written and the supplier's could not, so the supplier screen's date filter had
     * nothing behind it - the totals it showed were always the whole history.
     * <p>
     * With no dates it reads the totals view, which has already summed everything; with
     * dates it sums the period itself, since a total cannot be filtered after the fact.
     */
    public List<SupplierAccount> getTotalsAccount(String dateFrom, String dateTo) throws DaoException {
        boolean wholeHistory = dateFrom == null || dateTo == null;
        String selectAccountAndTotalsById = wholeHistory ? totalsSql() : totalsBetweenDatesSql();
        GenericMapper<SupplierAccount> map = rs -> {
            SupplierAccount model = new SupplierAccount();
            int codeSup = rs.getInt(ACCOUNT_CODE);
            String nameSup = rs.getString(NAME);
            double purchase = rs.getDouble(PURCHASE);
            double discount = rs.getDouble(DISCOUNT);
            double amount = rs.getDouble(AMOUNT);
            model.setId(0);
            model.setDate(rs.getString(ACCOUNT_DATE));
            model.setPurchase(purchase - discount);
            model.setPaid(rs.getDouble(PAID));
            model.setAmount(amount);
            model.setSuppliers(new Suppliers(codeSup, nameSup));
            return model;
        };

        if (wholeHistory) {
            return queryForObjects(selectAccountAndTotalsById, map);
        }
        return queryForObjects(selectAccountAndTotalsById, map, dateFrom, dateTo);
    }

    public List<SupplierAccount> getAccountBetweenDate(String dateFrom, String dateTo) throws DaoException {
        String query = "SELECT * FROM suppliers_accounts ca\n" +
                "join suppliers c on c.id = ca.account_code\n" +
                "where ca.account_date between ? and ? order by ca.account_date ";
        GenericMapper<SupplierAccount> mapMain = resultSet -> {
            SupplierAccount map = mapMain(resultSet);
            map.setSuppliers(new Suppliers(resultSet.getInt(ACCOUNT_CODE), resultSet.getString(NAME)));
            return map;
        };
        return queryForObjects(query, mapMain, dateFrom, dateTo);
    }
}
