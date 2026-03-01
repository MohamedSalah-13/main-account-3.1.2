package com.hamza.account.model.dao;

import lombok.Setter;

import java.sql.Connection;

public enum DaoFactory {

    INSTANCE;

    @Setter
    private Connection connection;

    public CompanyDao getCompanyDao() {
        return new CompanyDao(connection);
    }

    public ItemsDao getItemsDao() {
        return new ItemsDao(connection, this);
    }

    public TypeSelPriceDao getItemsSelPriceDao() {
        return new TypeSelPriceDao(connection);
    }

    public ItemsUnitDao getItemsUnitDao() {
        return new ItemsUnitDao(connection, this);
    }

    public Items_StockDao getItemsStockDao() {
        return new Items_StockDao(connection, this);
    }

    public MainGroupsDao getMainGroups() {
        return new MainGroupsDao(connection);
    }

    public SubGroupsDao getSupGroupsDao() {
        return new SubGroupsDao(connection, this);
    }

    public EmployeesDao employeesDao() {
        return new EmployeesDao(connection);
    }

    public CustomerDao customersDao() {
        return new CustomerDao(connection, this);
    }

    public SuppliersDao getSuppliersDao() {
        return new SuppliersDao(connection, this);
    }

    public PurchaseDao purchaseDao() {
        return new PurchaseDao(connection, this);
    }

    public PurchaseReturnDao purchaseReturnsDao() {
        return new PurchaseReturnDao(connection, this);
    }

    public SalesDao salesDao() {
        return new SalesDao(connection, this);
    }

    public SalesPackageDao salesPackageDao() {
        return new SalesPackageDao(connection);
    }

    public SalesReturnDao salesReturnsDao() {
        return new SalesReturnDao(connection, this);
    }

    public TotalsBuyDao totalsPurchaseDao() {
        return new TotalsBuyDao(connection, this);
    }

    public TotalsSalesDao totalsSalesDao() {
        return new TotalsSalesDao(connection, this);
    }

    public StockDao stockDao() {
        return new StockDao(connection);
    }

    public StockTransferDao stockTransferDao() {
        return new StockTransferDao(connection, this);
    }

    public StockTransferListDao stockTransferListDao() {
        return new StockTransferListDao(connection, this);
    }

    public UsersDao usersDao() {
        return new UsersDao(connection);
    }

    public UnitsDao unitsDao() {
        return new UnitsDao(connection);
    }

    public TreasuryDao treasuryDao() {
        return new TreasuryDao(connection);
    }

    public TreasuryTransferDao treasuryTransferDao() {
        return new TreasuryTransferDao(connection);
    }

    public CustomerAccountDao customerAccountDao() {
        return new CustomerAccountDao(connection);
    }

    public SupplierAccountDao suppliersAccountDao() {
        return new SupplierAccountDao(connection);
    }

    public CardItemDao cardItemDao() {
        return new CardItemDao(connection);
    }

    public TotalsPurchaseReturnDao totalsBuyReturnDao() {
        return new TotalsPurchaseReturnDao(connection, this);
    }

    public TotalsSalesReturnDao totalsSalesReturnDao() {
        return new TotalsSalesReturnDao(connection, this);
    }

    public Processes_Dao processesDao() {
        return new Processes_Dao(connection);
    }

    public ExpensesDetailsDao expensesDetailsDao() {
        return new ExpensesDetailsDao(connection);
    }

    public ExpensesDao expensesDao() {
        return new ExpensesDao(connection);
    }

    public ItemMiniDao itemMiniDao() {
        return new ItemMiniDao(connection);
    }

    public TargetDetailsDao targetDetailsDao() {
        return new TargetDetailsDao(connection);
    }

    public TargetDao targetDao() {
        return new TargetDao(connection);
    }

    public TruncateDao truncateDao() {
        return new TruncateDao(connection);
    }

    public EarningsDao earningsDao() {
        return new EarningsDao(connection);
    }

    public DepositDao depositDao() {
        return new DepositDao(connection, this);
    }

    public TreasuryBalanceDao treasuryBalanceDao() {
        return new TreasuryBalanceDao(connection);
    }

    public UserPermissionDao userPermissionDao() {
        return new UserPermissionDao(connection);
    }

    public AreaDao areaDao() {
        return new AreaDao(connection);
    }

    public ItemsPackageDao getItemsPackageDao() {
        return new ItemsPackageDao(connection);
    }
}
