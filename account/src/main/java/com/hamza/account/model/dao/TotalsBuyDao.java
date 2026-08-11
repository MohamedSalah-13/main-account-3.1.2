package com.hamza.account.model.dao;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.model.domain.*;
import com.hamza.account.trial.TrialManager;
import com.hamza.account.type.InvoiceStatus;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class TotalsBuyDao extends AbstractDao<Total_buy> {

    /** Which document this DAO writes. The period lock it must respect follows from it. */
    static final DocumentType DOCUMENT_TYPE = DocumentType.PURCHASE;

    /** Where it lives, and every statement that reads or writes it. */
    static final DocumentTableSpec SPEC = DocumentTableSpec.of(DOCUMENT_TYPE);

    // The names map() reads the result set by - see TotalsSalesDao.
    private final String TABLE_VIEW = SPEC.view();
    private final String INVOICE_NUMBER = SPEC.key();
    private final String SUP_CODE = SPEC.party();
    private final String INVOICE_DATE = SPEC.dateColumn();
    private final String PAID_UP = SPEC.paid();
    private final String DATE_INSERT = "date_insert";
    private final String INVOICE_TYPE = "invoice_type";
    private final String TOTAL = "total";
    private final String DISCOUNT = "discount";
    private final String DISCOUNT_TYPE = "discount_type";
    private final String STOCK_ID = "stock_id";
    private final String TREASURY_ID = "treasury_id";
    private final String NOTES = "notes";
    private final String OTHER_PAID = "OtherPaid";
    private final String USER_ID = "user_id";
    private final PurchaseDao purchaseDao;
    private final DaoFactory daoFactory;

    TotalsBuyDao(DaoFactory daoFactory) {
        super();
        this.daoFactory = daoFactory;
        this.purchaseDao = daoFactory.purchaseDao();
    }

    @Override
    public List<Total_buy> loadAll() throws DaoException {
        return queryForObjects(selectAllSql(), this::map);
    }

    @Override
    public List<Total_buy> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        return queryForObjects(selectBetweenDatesSql(), this::map, startDate, endDate);
    }

    @Override
    public int insert(Total_buy total_buy) throws DaoException {
        // See TotalsSalesDao.insert: enforced here so it holds for every caller.
        PeriodLock.require(total_buy.getDate(), DOCUMENT_TYPE.periodLock().label());
        if (!withConnection(c -> new TrialManager(c).canAddPurchase())) return 0;
        String query = insertSql();
        return insertMultiData(() -> {
            Object[] data = getData(total_buy);
            // first insert data in total
            executeUpdateWithException(query, data);
            // Secondly, enter the purchase data.
            purchaseDao.insertList(total_buy.getPurchaseList());
        });
    }

    @Override
    public int update(Total_buy total_buy) throws DaoException {
        PeriodLock.requireMove(DOCUMENT_TYPE.periodLock(), total_buy.getId(), total_buy.getDate());
        String query = updateSql();
        return insertMultiData(() -> {
            Object[] data = getUpdateData(total_buy);

            executeUpdateWithException(query, data);
            // first, delete data from purchase
            executeUpdateWithException(lineDeleteByDocumentSql(), total_buy.getId());
            // Secondly, enter the purchase data.
            // insert if not existing
            List<Purchase> purchaseList = total_buy.getPurchaseList();
            purchaseDao.insertList(purchaseList);
            // update list if existing
//            purchaseDao.updateList(list1);
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(deleteSql(), id);
    }

    @Override
    public Total_buy getDataById(int id) throws DaoException {
        return queryForObject(selectByIdSql(), this::map, id);
    }

    // ---- the statements, and the order their parameters go in -------------------
    // See TotalsSalesDao. Note how little of this family differs from the sales one -
    // no delegate, and the user and invoice number the other way round.

    String selectAllSql() {
        return SPEC.selectAllSql();
    }

    String selectBetweenDatesSql() {
        return SPEC.selectBetweenDatesSql();
    }

    String selectByPartySql() {
        return SPEC.selectByPartySql();
    }

    String selectByYearSql() {
        return SPEC.selectByYearSql();
    }

    String deleteInRangeSql(int count) {
        return SPEC.deleteInRangeSql(count);
    }

    /** Its lines go with it, and are rewritten wholesale on every update. */
    String lineDeleteByDocumentSql() {
        return SPEC.lineDeleteByDocumentSql();
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

    String selectByIdSql() {
        return SPEC.selectByIdSql();
    }

    String maxIdSql() {
        return SPEC.maxIdSql();
    }

    /** The parameters of {@link #updateSql()}, which is not the order {@link #getData} uses. */
    Object[] getUpdateData(Total_buy total_buy) {
        return new Object[]{total_buy.getSupplierData().getId()
                , total_buy.getInvoiceType().getId()
                , total_buy.getDate()
                , total_buy.getTotal()
                , total_buy.getDiscount()
                , total_buy.getPaid()
                , total_buy.getStockData().getId()
                , total_buy.getTreasuryModel().getId()
                , total_buy.getNotes()
                , total_buy.getId()
        };
    }

    @Override
    public Object[] getData(Total_buy total_buy) throws DaoException {
        return new Object[]{total_buy.getSupplierData().getId()
                , total_buy.getInvoiceType().getId()
                , total_buy.getDate()
                , total_buy.getTotal()
                , total_buy.getDiscount()
                , total_buy.getPaid()
                , total_buy.getStockData().getId()
                , total_buy.getTreasuryModel().getId()
                , total_buy.getNotes()
                , total_buy.getUsers().getId()
                , total_buy.getId()
        };
    }

    @Override
    public Total_buy map(ResultSet rs) throws DaoException {
        Total_buy total_buy;
        try {
            int num = rs.getInt(INVOICE_NUMBER);
            int sup_id = rs.getInt(SUP_CODE);
            String sup_name = rs.getString(SuppliersDao.NAME);
            int type_id = rs.getInt(INVOICE_TYPE);
            String date = rs.getString(INVOICE_DATE);
            double total = rs.getDouble(TOTAL);
            double dis = rs.getDouble(DISCOUNT);
            double paid = rs.getDouble(PAID_UP);
            int stock_id = rs.getInt(STOCK_ID);
            double total_amount = total - dis;
            String stock_name = rs.getString(StockDao.STOCK_NAME);
            int treasury_id = rs.getInt(TREASURY_ID);
            String treasury_name = rs.getString(TreasuryDao.COLUMN_NAME);

            total_buy = new Total_buy();
            total_buy.setId(num);
            total_buy.setInvoiceType(type_id == 1 ? InvoiceType.CASH : InvoiceType.DEFER);
            total_buy.setDate(date);
            total_buy.setTotal(total);
            total_buy.setDiscount(dis);
            total_buy.setTotal_after_discount(total_amount);
            total_buy.setPaid(paid);
            total_buy.setRest(roundToTwoDecimalPlaces(total_amount - paid));
            total_buy.setSupplierData(new Suppliers(sup_id, sup_name));
            total_buy.setStockData(new Stock(stock_id, stock_name));
            total_buy.setTreasuryModel(new Treasury(treasury_id, treasury_name, BigDecimal.valueOf(0)));
            total_buy.setNotes(rs.getString(NOTES) != null ? rs.getString(NOTES) : "");
            total_buy.setOtherPaid(rs.getDouble(OTHER_PAID));
            total_buy.setAmountAfterOtherPaid(roundToTwoDecimalPlaces(total_amount - total_buy.getOtherPaid() - total_buy.getPaid()));
            total_buy.setInvoice_status(total_buy.getAmountAfterOtherPaid() == 0 ? InvoiceStatus.CLOSE : InvoiceStatus.OPEN);
            total_buy.setCreated_at(LocalDateTime.parse(rs.getString(DATE_INSERT), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            total_buy.setUsers(daoFactory.usersDao().getDataById(rs.getInt(USER_ID)));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return total_buy;
    }

    public int deleteInvoicesInRange(Integer... invoiceNumbers) throws DaoException {
        if (invoiceNumbers.length == 0) return 0;
        return executeUpdate(deleteInRangeSql(invoiceNumbers.length), (Object[]) invoiceNumbers);
    }

    public List<Total_buy> getTotalBuyBySupId(int customerId) throws DaoException {
        return queryForObjects(selectByPartySql(), this::map, customerId);
    }

    public List<Total_buy> getTotalBuyByYear(int year) throws DaoException {
        return queryForObjects(selectByYearSql(), this::map, year);
    }

    public List<Integer> getListYear() {
        return queryForIntList(yearsSql());
    }

    /**
     * Every year any of the four documents was written in - سنوات الحركة، من الأحدث
     * للأقدم. It lives on the purchase DAO for no reason other than that being where it
     * was needed first: it names all four tables and belongs to none of them, so the
     * statement itself is now built from the four specs.
     */
    static String yearsSql() {
        return DocumentTableSpec.yearsSql();
    }

    public int getMaxId() throws DaoException {
        return queryForInt(maxIdSql());
    }

}
