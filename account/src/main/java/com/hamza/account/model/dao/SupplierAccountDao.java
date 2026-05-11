package com.hamza.account.model.dao;

import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.TableName;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.GenericMapper;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class SupplierAccountDao extends AbstractDao<SupplierAccount> {

    public static final String SELECT_ACCOUNT_AND_TOTALS_BY_ID = """
            SELECT ac.account_num,
                   ac.account_code,
                   ac.account_date,
                   ac.purchase,
                   ac.discount,
                   ac.paid,
                   ROUND(ac.purchase - ac.discount - ac.paid) as amount,
                   ac.notes,
                   s.name,
                   ac.information,
                   ac.type,
                   ac.date_insert,
                   ac.treasury_id,
                   ac.numberInv
            FROM account_suppliers_table ac
                     JOIN suppliers s ON ac.account_code = s.id """;
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
    private final String DATE_INSERT = "date_insert";
    private final String USER_ID = "user_id";
    private final String NAME = "name";

    SupplierAccountDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<SupplierAccount> loadAll() throws DaoException {
//        String selectAccountAndTotalsById = SqlStatements.selectStatement(TABLE_VIEW).concat(" order by account_date ");
        return queryForObjects(SELECT_ACCOUNT_AND_TOTALS_BY_ID.concat(" ORDER BY ac.date_insert"), this::map);
    }

    @Override
    public List<SupplierAccount> loadAllById(int id) throws DaoException {
        return queryForObjects(SELECT_ACCOUNT_AND_TOTALS_BY_ID.concat(" where account_code = ? and ac.information =2"), this::map, id);
    }

    @Override
    public int insert(SupplierAccount model) throws DaoException {
        String s = SqlStatements.insertStatement(TABLE_NAME, ACCOUNT_CODE, ACCOUNT_DATE, PAID, NOTES, NUMBER_INV, TREASURY_ID, ACCOUNT_NUM, USER_ID);
        return executeUpdate(s, getData(model));
    }

    @Override
    public int update(SupplierAccount supplierAccount) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ACCOUNT_NUM, ACCOUNT_CODE, ACCOUNT_DATE, PAID, NOTES, NUMBER_INV, TREASURY_ID), new Object[]{
                supplierAccount.getSuppliers().getId()
                , supplierAccount.getDate()
                , supplierAccount.getPaid()
                , supplierAccount.getNotes()
                , supplierAccount.getInvoice_number()
                , supplierAccount.getTreasury().getId()
                , supplierAccount.getId()
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ACCOUNT_NUM), id);
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
            model.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            String nameSup = adjustPurchase ? rs.getString(NAME) : "";
            model.setSuppliers(new Suppliers(codeSup, nameSup));
            if (adjustPurchase) {
                var tableNameById = TableName.getTableNameById(rs.getInt(INFORMATION));
                model.setInformation(tableNameById);
                model.setInformation_name(Objects.requireNonNull(tableNameById).getType());
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
        String selectAccountById = SqlStatements.selectStatement(TABLE_NAME)
                .concat(" join treasury t on t.id = suppliers_accounts.treasury_id ")
                .concat(" WHERE ").concat(ACCOUNT_NUM).concat(" = ?");
        GenericMapper<SupplierAccount> mapMain = resultSet -> {
            SupplierAccount map = mapMain(resultSet);
            map.getTreasury().setName(resultSet.getString("t_name"));
            return map;
        };
        return queryForObject(selectAccountById, mapMain, id);
    }

    public List<SupplierAccount> getAccountByAccountCode(int accountCode) throws DaoException {
        String selectAccountById = SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ACCOUNT_CODE);
        return queryForObjects(selectAccountById, this::mapMain, accountCode);
    }

    /**
     * Retrieves the summary of accounts and the total amounts of purchases and payments for each supplier.
     *
     * @return a list of SupplierAccount objects containing the account summaries and totals for each supplier.
     * @throws DaoException if there is an error during the data access operation.
     */
    public List<SupplierAccount> getTotalsAccount() throws DaoException {
        String selectAccountAndTotalsById = SqlStatements.selectStatement(TABLE_VIEW_TOTALS).concat(" order by name ");
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

        return queryForObjects(selectAccountAndTotalsById, map);
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
