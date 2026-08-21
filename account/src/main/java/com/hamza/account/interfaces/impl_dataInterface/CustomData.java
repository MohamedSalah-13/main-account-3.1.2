package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.document.DocumentType;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.features.events.InvoiceSide;
import com.hamza.account.features.invoice.InvoiceSaveCommand;
import com.hamza.account.features.invoice.InvoiceSaveResult;
import com.hamza.account.features.invoice.InvoiceSaveService;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_namesDao.CustomerAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalSalesImpDesign;
import com.hamza.account.interfaces.names.CustomerName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.service.SalesService;
import com.hamza.account.service.TotalSalesService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;

public class CustomData extends LoadData implements DataInterface<Sales, Total_Sales, Customers, CustomerAccount> {

    private final TotalSalesService totalSalesService = ServiceRegistry.get(TotalSalesService.class);
    private final SalesService salesService = ServiceRegistry.get(SalesService.class);

    private final DesignInterface designInterface = () -> DocumentType.SALES;

    private final TotalDesignInterface<Total_Sales> totalDesignInterface = new TotalSalesImpDesign(this, totalSalesService);

    private final InvoiceBuy<Sales, Total_Sales, Customers, CustomerAccount> invoiceBuy = new SalesInvoice();

    private final NameData<Customers> nameData = new CustomerName();

    private final NameAndAccountInterface<Customers, CustomerAccount> nameAndAccountInterface = new CustomerAndAccount();

    private final AccountData<CustomerAccount> accountData = new AccountCustomer(daoFactory);


    private final TotalsAndPurchaseList<Sales, Total_Sales> totalsAndPurchaseList = new TotalsAndPurchaseList<>() {
        @Override
        public DaoList<Total_Sales> totalDao() {
            return daoFactory.totalsSalesDao();
        }

        @Override
        public List<Total_Sales> totalList(String dateFrom, String dateTo) throws DaoException {
            return totalSalesService.getTotalSalesByDateRange(dateFrom, dateTo);
        }

        @Override
        public List<Sales> purchaseOrSalesList(int from, int to) throws DaoException {
//                return daoFactory.salesDao().loadBetweenTwoInvoiceNumber(from, to);
            return salesService.findBetweenTwoInvoiceNumber(from, to);
        }

        @Override
        public int getMaxId() throws Exception {
            return totalSalesService.getMaxId();
        }

        @Override
        public List<Total_Sales> searchTotals(TotalsSearchCriteria criteria) throws DaoException {
            return totalSalesService.searchTotals(criteria);
        }
    };

    public CustomData(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return designInterface;
    }

    @Override
    public TotalDesignInterface<Total_Sales> totalDesignInterface() {
        return totalDesignInterface;
    }

    @Override
    public InvoiceSide invoiceSide() {
        return InvoiceSide.SALES;
    }

    @Override
    public List<Sales> listForAllPurchase(int id) throws DaoException {
//        return daoFactory.salesDao().loadAllById(id);
        return salesService.fetchByInvoiceNumber(id);
    }

    @Override
    public InvoiceBuy<Sales, Total_Sales, Customers, CustomerAccount> invoiceBuy() {
        return invoiceBuy;
    }

    @Override
    public NameData<Customers> nameData() {
        return nameData;
    }

    @Override
    public TotalsAndPurchaseList<Sales, Total_Sales> totalsAndPurchaseList() {
        return totalsAndPurchaseList;
    }

    @Override
    public NameAndAccountInterface<Customers, CustomerAccount> nameAndAccountInterface() throws Exception {
        return nameAndAccountInterface;
    }

    @Override
    public AccountData<CustomerAccount> accountData() {
        return accountData;
    }

    /**
     * The save pipeline is built here, where T1/T2 are still concrete classes, and is
     * reached from the invoice screen only through this method - which names neither.
     * Built per save rather than kept in a field: its constructor reads the two return
     * settings, and the settings screen writes them while the app is running.
     */
    @Override
    public InvoiceSaveResult saveInvoice(InvoiceSaveCommand command) throws DaoException {
        InvoiceSaveService<Sales, Total_Sales, Customers, CustomerAccount> invoiceSaveService =
                new InvoiceSaveService<>(invoiceBuy, totalsAndPurchaseList,
                        designInterface.documentType(),
                        ServiceRegistry.get(TreasuryService.class)::getTreasuryByName,
                        ServiceRegistry.get(EmployeeService.class)::getDelegateByName);
        return invoiceSaveService.save(command);
    }

    @Override
    public void addList(List<Total_Sales> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (Total_Sales totalSales : items) {
            var listPrint = listForAllPurchase(totalSales.getId());
            for (Sales value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                purchase.setNum(value.getInvoiceNumber());
                purchase.setName(totalSales.getCustomers().getName());
                purchase.setDate(totalSales.getDate());
                purchase.setPrice(value.getPrice());
                purchase.setDiscount(value.getDiscount());
                purchase.setQuantity(value.getQuantity());
                purchase.setTotal(value.getTotal());
                purchase.setUnitsType(value.getUnitsType());
                purchase.setItemName(value.getItems().getNameItem());
                printPurchaseWithNames.add(purchase);
            }
        }
    }
}
