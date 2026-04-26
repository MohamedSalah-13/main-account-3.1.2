package com.hamza.account.controller.others;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.service.*;
import lombok.Getter;


@Getter
public class ServiceData {

    protected final ItemsService itemsService;
    protected final StockService stockService;
    protected final StockTransferService stockTransferService;
    protected final StockTransferListService stockTransferListService;
    protected final EmployeeService employeeService;
    protected final TreasuryService treasuryService;
    protected final UnitsService unitsService;
    protected final UsersService usersService;
    protected final MainGroupService mainGroupService;
    protected final SupGroupService supGroupService;
    protected final TreasuryTransferService treasuryTransferService;
    protected final CardItemService cardItemService;
    protected final TargetDetailsService targetDetailsService;
    protected final TargetService targetService;
    protected final ExpensesService expensesService;
    protected final ExpensesDetailsService expensesDetailsService;
    protected final TotalSalesService totalSalesService;
    protected final TotalBuyService totalBuyService;
    protected final TotalBuyReturnService totalBuyReturnService;
    protected final TotalSalesReturnService totalSalesReturnService;
    protected final SalesService salesService;
    protected final PurchaseService purchaseService;
    protected final PurchaseReService purchaseReService;
    protected final SalesReService salesReService;
    protected final EarningsService earningsService;
    protected final CustomerService customerService;
    protected final SuppliersService suppliersService;
    protected final AccountCustomerService accountCustomerService;
    protected final AccountSupplierService accountSupplierService;
    protected final AuditLogService auditLogService;
    protected final DepositService depositService;
    protected final TreasuryBalanceService treasuryBalanceService;
    protected final ItemMiniQuantityService itemMiniQuantityService;
    protected final AreaService areaService;
    protected final SelPriceItemService selPriceItemService;
    protected final ItemPackageService itemPackageService;
    protected final UserShiftService userShiftService;

    public ServiceData(DaoFactory daoFactory) throws Exception {

        this.itemsService = new ItemsService(daoFactory, this);
        this.stockService = new StockService(daoFactory);
        this.stockTransferService = new StockTransferService(daoFactory);
        this.stockTransferListService = new StockTransferListService(daoFactory);
        this.employeeService = new EmployeeService(daoFactory);
        this.treasuryService = new TreasuryService(daoFactory);
        this.unitsService = new UnitsService(daoFactory);
        this.usersService = new UsersService(daoFactory);
        this.mainGroupService = new MainGroupService(daoFactory);
        this.supGroupService = new SupGroupService(daoFactory);
        this.treasuryTransferService = new TreasuryTransferService(daoFactory);
        this.cardItemService = new CardItemService(daoFactory);
        this.targetDetailsService = new TargetDetailsService(daoFactory);
        this.targetService = new TargetService(daoFactory);
        this.expensesService = new ExpensesService(daoFactory);
        this.expensesDetailsService = new ExpensesDetailsService(daoFactory);
        this.totalSalesService = new TotalSalesService(daoFactory);
        this.totalBuyService = new TotalBuyService(daoFactory);
        this.totalBuyReturnService = new TotalBuyReturnService(daoFactory);
        this.totalSalesReturnService = new TotalSalesReturnService(daoFactory);
        this.salesService = new SalesService(daoFactory);
        this.purchaseService = new PurchaseService(daoFactory);
        this.purchaseReService = new PurchaseReService(daoFactory);
        this.salesReService = new SalesReService(daoFactory);
        this.earningsService = new EarningsService(daoFactory);
        this.customerService = new CustomerService(daoFactory);
        this.suppliersService = new SuppliersService(daoFactory);
        this.accountCustomerService = new AccountCustomerService(daoFactory);
        this.accountSupplierService = new AccountSupplierService(daoFactory);
        this.auditLogService = new AuditLogService(daoFactory);
        this.depositService = new DepositService(daoFactory);
        this.treasuryBalanceService = new TreasuryBalanceService(daoFactory);
        this.itemMiniQuantityService = new ItemMiniQuantityService(daoFactory);
        this.areaService = new AreaService(daoFactory);
        this.selPriceItemService = new SelPriceItemService(daoFactory);
        this.itemPackageService = new ItemPackageService(daoFactory);
        this.userShiftService = new UserShiftService(daoFactory);
    }
}
