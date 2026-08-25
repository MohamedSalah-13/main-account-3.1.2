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
import com.hamza.account.document.InvoiceBuy;
import com.hamza.account.document.TotalsAndPurchaseList;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.interfaces.impl_namesDao.CustomerAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalSalesReturnImplDesign;
import com.hamza.account.interfaces.names.CustomerName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.service.SalesReService;
import com.hamza.account.service.TotalSalesReturnService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;

public class CustomDataReturn extends LoadData implements DataInterface<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> {

    private final TotalSalesReturnService totalSalesReturnService = ServiceRegistry.get(TotalSalesReturnService.class);
    private final SalesReService salesReService = ServiceRegistry.get(SalesReService.class);

    private final DesignInterface designInterface = () -> DocumentType.SALES_RETURN;

    private final TotalDesignInterface totalDesignInterface = new TotalSalesReturnImplDesign(totalSalesReturnService);

    private final InvoiceBuy<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> invoiceBuy = new SalesInvoiceReturn();

    private final NameData<Customers> nameData = new CustomerName();

    private final NameAndAccountInterface<Customers, CustomerAccount> nameAndAccountInterface = new CustomerAndAccount();

    private final AccountData<CustomerAccount> accountData = new AccountCustomer(daoFactory);

    private final TotalsAndPurchaseList<Sales_Return, Total_Sales_Re> totalsAndPurchaseList = new TotalsAndPurchaseList<>() {
        @Override
        public DaoList<Total_Sales_Re> totalDao() {
            return daoFactory.totalsSalesReturnDao();
        }

        @Override
        public List<Total_Sales_Re> totalList(String dateFrom, String dateTo) throws DaoException {
//                return daoFactory.totalsSalesReturnDao().loadDataBetweenDate(dateFrom, dateTo);
            return totalSalesReturnService.getTotalSalesByDateRange(dateFrom, dateTo);
        }

        @Override
        public List<Sales_Return> purchaseOrSalesList(int from, int to) throws DaoException {
            return salesReService.findBetweenTwoInvoiceNumber(from, to);
        }

        @Override
        public int getMaxId() throws Exception {
            return totalSalesReturnService.getMaxId();
        }

        @Override
        public List<Total_Sales_Re> searchTotals(TotalsSearchCriteria criteria) throws DaoException {
            return totalSalesReturnService.searchTotals(criteria);
        }
    };

    public CustomDataReturn(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return designInterface;
    }

    @Override
    public TotalDesignInterface totalDesignInterface() {
        return totalDesignInterface;
    }

    @Override
    public InvoiceSide invoiceSide() {
        return InvoiceSide.SALES;
    }

    @Override
    public List<Sales_Return> listForAllPurchase(int i) throws DaoException {
//        return daoFactory.salesReturnsDao().loadAllById(i);
        return salesReService.fetchByInvoiceNumber(i);
    }

    @Override
    public InvoiceBuy<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> invoiceBuy() {
        return invoiceBuy;
    }

    @Override
    public NameData<Customers> nameData() {
        return nameData;
    }

    @Override
    public TotalsAndPurchaseList<Sales_Return, Total_Sales_Re> totalsAndPurchaseList() {
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
        InvoiceSaveService<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> invoiceSaveService =
                new InvoiceSaveService<>(invoiceBuy, totalsAndPurchaseList,
                        designInterface.documentType(),
                        ServiceRegistry.get(TreasuryService.class)::getTreasuryByName,
                        ServiceRegistry.get(EmployeeService.class)::getDelegateByName);
        return invoiceSaveService.save(command);
    }

    @Override
    public void addList(List<? extends BaseTotals> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (BaseTotals row : items) {
            Total_Sales_Re totalSalesRe = (Total_Sales_Re) row;
            var listPrint = listForAllPurchase(totalSalesRe.getId());
            for (Sales_Return value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                purchase.setNum(value.getInvoiceNumber());
                purchase.setName(totalSalesRe.getCustomer().getName());
                purchase.setDate(totalSalesRe.getDate());
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
