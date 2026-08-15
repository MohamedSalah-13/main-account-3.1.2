package com.hamza.account.model.dao;

import com.hamza.account.features.inventory.InventoryDao;
import com.hamza.account.features.stockcount.StockCountDao;
import com.hamza.account.period.PeriodLockDao;

/**
 * Creates DAOs. It no longer holds a {@link java.sql.Connection}: each DAO call
 * borrows one from the pool for the length of that call, so there is nothing
 * here to hand out or to keep alive.
 * <p>
 * The {@code setAuditUserId}/{@code clearAuditUserId} pair that used to live here
 * was removed with it. Both set a MySQL session variable, which belongs to one
 * connection and so cannot survive pooling - and nothing called them: no code
 * referenced either method and no trigger in the schema read {@code @app_user_id}.
 */
public enum DaoFactory {

    INSTANCE;

    public CompanyDao getCompanyDao() {
        return new CompanyDao();
    }

    public ItemsDao getItemsDao() {
        return new ItemsDao(this);
    }

    public TypeSelPriceDao getItemsSelPriceDao() {
        return new TypeSelPriceDao();
    }

    public ItemsUnitDao getItemsUnitDao() {
        return new ItemsUnitDao(this);
    }

    public ItemBarcodesDao getItemBarcodesDao() {
        return new ItemBarcodesDao();
    }

    public Items_StockDao getItemsStockDao() {
        return new Items_StockDao(this);
    }

    public InventoryDao inventoryDao() {
        return new InventoryDao();
    }

    public StockCountDao stockCountDao() {
        return new StockCountDao();
    }

    public PeriodLockDao periodLockDao() {
        return new PeriodLockDao();
    }

    public MainGroupsDao getMainGroups() {
        return new MainGroupsDao();
    }

    public SubGroupsDao getSupGroupsDao() {
        return new SubGroupsDao(this);
    }

    public EmployeesDao employeesDao() {
        return new EmployeesDao();
    }

    public CustomerDao customersDao() {
        return new CustomerDao(this);
    }

    public SuppliersDao getSuppliersDao() {
        return new SuppliersDao(this);
    }

    public PurchaseDao purchaseDao() {
        return new PurchaseDao(this);
    }

    public PurchaseReturnDao purchaseReturnsDao() {
        return new PurchaseReturnDao(this);
    }

    public SalesDao salesDao() {
        return new SalesDao(this);
    }

    public SalesReturnDao salesReturnsDao() {
        return new SalesReturnDao(this);
    }

    public TotalsBuyDao totalsPurchaseDao() {
        return new TotalsBuyDao(this);
    }

    public TotalsSalesDao totalsSalesDao() {
        return new TotalsSalesDao(this);
    }

    public StockDao stockDao() {
        return new StockDao();
    }

    public UsersDao usersDao() {
        return new UsersDao();
    }

    public UnitsDao unitsDao() {
        return new UnitsDao();
    }

    public TreasuryDao treasuryDao() {
        return new TreasuryDao();
    }

    public CustomerAccountDao customerAccountDao() {
        return new CustomerAccountDao();
    }

    public SupplierAccountDao suppliersAccountDao() {
        return new SupplierAccountDao();
    }

    public CardItemDao cardItemDao() {
        return new CardItemDao();
    }

    public TotalsPurchaseReturnDao totalsBuyReturnDao() {
        return new TotalsPurchaseReturnDao(this);
    }

    public TotalsSalesReturnDao totalsSalesReturnDao() {
        return new TotalsSalesReturnDao(this);
    }

    public AuditLogDao processesDao() {
        return new AuditLogDao();
    }

    public ExpensesDetailsDao expensesDetailsDao() {
        return new ExpensesDetailsDao();
    }

    public ExpensesDao expensesDao() {
        return new ExpensesDao();
    }

    public ItemMiniDao itemMiniDao() {
        return new ItemMiniDao();
    }

    public EarningsDao earningsDao() {
        return new EarningsDao();
    }


    public TreasuryBalanceDao treasuryBalanceDao() {
        return new TreasuryBalanceDao();
    }

    public AreaDao areaDao() {
        return new AreaDao();
    }

    public UserShiftDao userShiftDao() {
        return new UserShiftDao();
    }

    public DailyDashboardReportDao dailyDashboardReportDao() {
        return new DailyDashboardReportDao();
    }

    public TopSellingItemDao topSellingItemDao() {
        return new TopSellingItemDao();
    }

    public MonthlySalesViewDao monthlySalesViewDao() {
        return new MonthlySalesViewDao("view_monthly_sales");
    }

    public MonthlySalesViewDao monthlyPurchaseViewDao() {
        return new MonthlySalesViewDao("view_monthly_purchase");
    }

    public CustomerPurchasedItemDao customerPurchasedItemDao() {
        return new CustomerPurchasedItemDao();
    }

    public SuppliersSalesItemDao suppliersSalesItemDao() {
        return new SuppliersSalesItemDao();
    }

    public TableDataReportsDao tableDataReportsDao() {
        return new TableDataReportsDao();
    }

    public ItemSalesRankDao itemSalesRankDao() {
        return new ItemSalesRankDao();
    }

    public DailyItemSalesDao dailyItemSalesDao() {
        return new DailyItemSalesDao();
    }

    public ComprehensiveSalesDao comprehensiveSalesDao() {
        return new ComprehensiveSalesDao();
    }

    public CustomerReceivableDao customerReceivableDao() {
        return new CustomerReceivableDao();
    }

    public DailySalesPointDao dailySalesPointDao() {
        return new DailySalesPointDao();
    }

    public DashboardPeriodDao dashboardPeriodDao() {
        return new DashboardPeriodDao();
    }

}
