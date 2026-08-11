package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Area;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
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

public class CustomerAccountDao extends AbstractDao<CustomerAccount> {

    /** Where a customer's payments live, and every statement over them. */
    static final PartyLedgerSpec SPEC = PartyLedgerSpec.CUSTOMER;

    /** Kept public: the customer statement screen builds on it. */
    public static final String SELECT_ACCOUNT_AND_TOTALS_BY_ID = SPEC.statementSql();
    private final String PURCHASE = "purchase";
    private final String INFORMATION = "information"; // id for name arabic
    private final String DISCOUNT = "discount";
    private final String ACCOUNT_CODE = "account_code";
    private final String ACCOUNT_NUM = "account_num";
    private final String ACCOUNT_DATE = "account_date";
    private final String PAID = "paid";
    private final String NOTES = "notes";
    private final String NUMBER_INV = "numberInv";
    private final String TABLE_NAME = "customers_accounts";
    private final String TABLE_VIEW = "account_customer_table";
    private final String TABLE_VIEW_TOTALS = "account_customer_totals";
    private final String AMOUNT = "amount";
    private final String TREASURY_ID = "treasury_id";
    private final String dateInsert = "created_at";
    private final String NAME = "name";
    private final String USER_ID = "user_id";

    CustomerAccountDao() {
        super();
    }

    @Override
    public List<CustomerAccount> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<CustomerAccount> loadAllById(int id) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, id);
    }

    // ---- the statements ---------------------------------------------------------
    // Named so PartyLedgerStatementsTest can read them without a database. The supplier
    // ledger is the same file under other names; what differs is pinned there.

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

    String betweenDatesSql() {
        return SPEC.betweenDatesSql();
    }

    @Override
    public int insert(CustomerAccount customerAccount) throws DaoException {
        // A payment is a dated document: it changes what the customer owed on that day
        // and on every day after it, so it may not be written into a reported month.
        //
        // Only payments live in this table. An invoice's own line on the statement is
        // not stored here - account_customer_table unions customers_accounts with
        // total_sales - so nothing an invoice save does reaches this check, and the
        // invoice is guarded at TotalsSalesDao instead.
        PeriodLock.require(customerAccount.getDate(), PeriodLockRegistry.CUSTOMER_ACCOUNT.label());
        String sqlQuery = insertSql();
        var objects = new Object[]{customerAccount.getCustomers().getId()
                , customerAccount.getDate()
                , customerAccount.getPaid()
                , customerAccount.getNotes()
                , customerAccount.getInvoice_number()
                , customerAccount.getTreasury().getId()
                , customerAccount.getId(), customerAccount.getUsers().getId()
        };
        return executeUpdate(sqlQuery, objects);
    }

    @Override
    public int update(CustomerAccount customerAccount) throws DaoException {
        // Both ends: where the payment is now, and where it is being moved to.
        PeriodLock.requireMove(PeriodLockRegistry.CUSTOMER_ACCOUNT, customerAccount.getId(), customerAccount.getDate());
        return executeUpdate(updateSql(), getData(customerAccount));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Object[] getData(CustomerAccount customerAccount) {
        return new Object[]{customerAccount.getCustomers().getId()
                , customerAccount.getDate()
                , customerAccount.getPaid()
                , customerAccount.getNotes()
                , customerAccount.getInvoice_number()
                , customerAccount.getTreasury().getId()
                , customerAccount.getUsers().getId()
                , customerAccount.getId()
        };
    }

    @Override
    public CustomerAccount map(ResultSet rs) throws DaoException {
        return createCustomerAccount(rs, true);
    }

    private CustomerAccount mapMain(ResultSet rs) throws DaoException {
        return createCustomerAccount(rs, false);
    }

    private CustomerAccount createCustomerAccount(ResultSet rs, boolean adjustPurchase) throws DaoException {
        CustomerAccount model;
        try {
            model = new CustomerAccount();
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
            var createdAt = rs.getString(dateInsert) == null ? rs.getString("created_at") : rs.getString(dateInsert);
            model.setCreated_at(LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            String customerName = adjustPurchase ? rs.getString(NAME) : "";
            model.setCustomers(new Customers(codeSup, customerName));
            if (adjustPurchase) {
                var tableNameById = TableName.getTableNameById(rs.getInt(INFORMATION));
                model.setInformation(tableNameById);
                model.setInformation_name(tableNameById.getType());
            }
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return model;
    }

    /**
     * Retrieves a CustomerAccount by its account number for the purpose of updating.
     *
     * @param id the account number of the CustomerAccount to retrieve
     * @return the CustomerAccount associated with the specified account number
     * @throws DaoException if an error occurs while accessing the data
     */
    public CustomerAccount getAccountByNumForUpdate(int id) throws DaoException {
        String selectAccountById = selectForUpdateSql();
        GenericMapper<CustomerAccount> mapMain = resultSet -> {
            CustomerAccount map = mapMain(resultSet);
            map.getTreasury().setName(resultSet.getString("t_name"));
            return map;
        };
        return queryForObject(selectAccountById, mapMain, id);
    }

    public List<CustomerAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        return queryForObjects(selectByPartyCodeSql(), this::mapMain, accountCode);
    }

    public List<CustomerAccount> getTotalsAccount(String dateFrom, String dateTo) throws DaoException {
        String selectAccountAndTotalsById = """
                SELECT account_code,
                       c.name,
                       SUM(purchase)                                       AS purchase,
                       SUM(discount)                                       AS discount,
                       SUM(paid)                                           AS paid,
                       round(sum(purchase) - sum(discount) - sum(paid), 2) AS amount,
                       MAX(account_date)                                   AS account_date
                FROM account_customer_table act
                         JOIN custom c ON act.account_code = c.id
                WHERE account_date between ? and ? and purchase>0
                GROUP BY account_code, name""";
        if (dateFrom == null || dateTo == null)
            selectAccountAndTotalsById = totalsSql();

        GenericMapper<CustomerAccount> map = rs -> {
            CustomerAccount model = new CustomerAccount();
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
            var customers = new Customers(codeSup, nameSup);
            customers.setArea(new Area(rs.getInt("area_id"), rs.getString("area_name")));
            model.setArea_id(rs.getInt("area_id"));
            model.setArea_name(rs.getString("area_name"));
            model.setCustomers(customers);
            return model;
        };

        if (dateFrom == null || dateTo == null)
            return queryForObjects(selectAccountAndTotalsById, map);

        return queryForObjects(selectAccountAndTotalsById, map, dateFrom, dateTo);
    }

    public List<CustomerAccount> getAccountBetweenDate(String dateFrom, String dateTo) throws DaoException {
        String query = "SELECT * FROM customers_accounts ca \n" +
                "join custom c on c.id = ca.account_code\n" +
                "where ca.account_date between ? and ? order by ca.account_date ";
        GenericMapper<CustomerAccount> mapMain = resultSet -> {
            CustomerAccount map = mapMain(resultSet);
            map.setCustomers(new Customers(resultSet.getInt(ACCOUNT_CODE), resultSet.getString(NAME)));
            return map;
        };
        return queryForObjects(query, mapMain, dateFrom, dateTo);
    }
}
