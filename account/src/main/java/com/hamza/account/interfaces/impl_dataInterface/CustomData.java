package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.interfaces.impl_design.DesignCustom;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoice;
import com.hamza.account.interfaces.impl_namesDao.CustomerAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalSalesImpDesign;
import com.hamza.account.interfaces.names.CustomerName;
import com.hamza.account.interfaces.spinner.CustomSpinner;
import com.hamza.account.interfaces.spinner.SpinnerInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.account.perm.PermCustomerAccountAndName;
import com.hamza.account.service.SalesService;
import com.hamza.account.service.TotalSalesService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.dateTime.DateUtils;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.dateTime.DateUtils.extractDay;
import static com.hamza.controlsfx.dateTime.DateUtils.extractMonth;

public class CustomData extends LoadData implements DataInterface<Sales, Total_Sales, Customers, CustomerAccount> {

    private final TotalSalesService totalSalesService = ServiceRegistry.get(TotalSalesService.class);
    private final SalesService salesService = ServiceRegistry.get(SalesService.class);

    public CustomData(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return new DesignCustom();
    }

    @Override
    public TotalDesignInterface<Total_Sales> totalDesignInterface() {
        return new TotalSalesImpDesign(this, totalSalesService);
    }

    @Override
    public Publisher<String> publisherPurchaseOrSales() {
        return dataPublisher.getPublisherSales();
    }

    @Override
    public List<Sales> listForAllPurchase(int id) throws DaoException {
//        return daoFactory.salesDao().loadAllById(id);
        return salesService.fetchByInvoiceNumber(id);
    }

    @Override
    public InvoiceBuy<Sales, Total_Sales, Customers, CustomerAccount> invoiceBuy() {
        return new SalesInvoice();
    }

    @Override
    public NameData<Customers> nameData() {
        return new CustomerName();
    }

    @Override
    public TotalsAndPurchaseList<Sales, Total_Sales> totalsAndPurchaseList() {
        return new TotalsAndPurchaseList<>() {
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
        };
    }

    @Override
    public NameAndAccountInterface<Customers, CustomerAccount> nameAndAccountInterface() throws Exception {
        return new CustomerAndAccount(daoFactory, dataPublisher);
    }

    @Override
    public FilterDateInterface<Total_Sales> filterDateInterface() {
        return new FilterDateInterface<>() {
            @Override
            public Predicate<Total_Sales> predicateByYear(int year) {
                return totalBuy -> DateUtils.extractYear(totalBuy.getDate()) == year;
            }

            @Override
            public Predicate<Total_Sales> predicateByMonth(int month) {
                return totalBuy -> extractMonth(totalBuy.getDate()) == month;
            }

            @Override
            public Predicate<Total_Sales> predicateByDay(int day) {
                return totalBuy -> extractDay(totalBuy.getDate()) == day;
            }
        };
    }

    @Override
    public SpinnerInterface<Customers, CustomerAccount> spinnerInterface() {
        return new CustomSpinner();
    }

    @Override
    public AccountData<CustomerAccount> accountData() {
        return new AccountCustomer(daoFactory);
    }

    @Override
    public PermAccountAndNameInt permAccountAndNameInt() {
        return new PermCustomerAccountAndName();
    }

    @Override
    public void loadNameAndAccount() {
//        LoadDataAndList.get2ListCustomers();
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
