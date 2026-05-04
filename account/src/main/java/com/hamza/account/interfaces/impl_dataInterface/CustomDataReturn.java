package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountCustomer;
import com.hamza.account.interfaces.impl_design.DesignCustomReturn;
import com.hamza.account.interfaces.impl_invoiceBuy.SalesInvoiceReturn;
import com.hamza.account.interfaces.impl_namesDao.CustomerAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalSalesReturnImplDesign;
import com.hamza.account.interfaces.names.CustomerName;
import com.hamza.account.interfaces.spinner.SpinnerInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;

public class CustomDataReturn extends LoadData implements DataInterface<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> {

    public CustomDataReturn(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return new DesignCustomReturn();
    }

    @Override
    public TotalDesignInterface<Total_Sales_Re> totalDesignInterface() {
        return new TotalSalesReturnImplDesign(this, daoFactory, this);
    }

    @Override
    public Publisher<String> publisherPurchaseOrSales() {
        return dataPublisher.getPublisherSales();
    }

    @Override
    public List<Sales_Return> listForAllPurchase(int i) throws DaoException {
//        return daoFactory.salesReturnsDao().loadAllById(i);
        return salesReService.fetchByInvoiceNumber(i);
    }

    @Override
    public InvoiceBuy<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> invoiceBuy() {
        return new SalesInvoiceReturn();
    }

    @Override
    public NameData<Customers> nameData() {
        return new CustomerName();
    }

    @Override
    public TotalsAndPurchaseList<Sales_Return, Total_Sales_Re> totalsAndPurchaseList() {
        return new TotalsAndPurchaseList<>() {
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
        };
    }

    @Override
    public NameAndAccountInterface<Customers, CustomerAccount> nameAndAccountInterface() throws Exception {
        return new CustomerAndAccount(daoFactory, dataPublisher);
    }

    @Override
    public FilterDateInterface<Total_Sales_Re> filterDateInterface() {
        return null;
    }

    @Override
    public SpinnerInterface<Customers, CustomerAccount> spinnerInterface() {
        return null;
    }

    @Override
    public AccountData<CustomerAccount> accountData() {
        return new AccountCustomer(daoFactory);
    }

    @Override
    public PermAccountAndNameInt permAccountAndNameInt() {
        return null;
    }


    @Override
    public void loadNameAndAccount() {
//        LoadDataAndList.get2ListCustomers();
    }

    @Override
    public void addList(List<Total_Sales_Re> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (Total_Sales_Re totalSalesRe : items) {
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
