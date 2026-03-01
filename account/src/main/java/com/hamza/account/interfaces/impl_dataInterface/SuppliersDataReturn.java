package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.controller.invoice.InvoiceDraftService;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model_print.PrintPurchaseWithName;
import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.interfaces.impl_design.DesignSuppliersReturn;
import com.hamza.account.interfaces.impl_invoiceBuy.PurchaseInvoiceReturn;
import com.hamza.account.interfaces.impl_namesDao.SupplierAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalsPurchaseReturnImplDesign;
import com.hamza.account.interfaces.names.SupplierName;
import com.hamza.account.interfaces.spinner.SpinnerInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.perm.PermAccountAndNameInt;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;
import com.hamza.controlsfx.observer.Publisher;

import java.util.List;

public class SuppliersDataReturn extends LoadData implements DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> {

    public SuppliersDataReturn(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return new DesignSuppliersReturn();
    }

    @Override
    public TotalDesignInterface<Total_Buy_Re> totalDesignInterface() {
        return new TotalsPurchaseReturnImplDesign(this, daoFactory, this);
    }

    @Override
    public Publisher<String> publisherPurchaseOrSales() {
        return dataPublisher.getPublisherBuy();
    }

    @Override
    public List<Purchase_Return> listForAllPurchase(int i) throws DaoException {
//        return daoFactory.purchaseReturnsDao().loadAllById(i);
        return purchaseReService.fetchByInvoiceNumber(i);
    }

    @Override
    public InvoiceBuy<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> invoiceBuy() {
        return new PurchaseInvoiceReturn();
    }

    @Override
    public NameData<Suppliers> nameData() {
        return new SupplierName();
    }

    @Override
    public TotalsAndPurchaseList<Purchase_Return, Total_Buy_Re> totalsAndPurchaseList() {
        return new TotalsAndPurchaseList<>() {
            @Override
            public DaoList<Total_Buy_Re> totalDao() {
                return daoFactory.totalsBuyReturnDao();
            }

            @Override
            public List<Total_Buy_Re> totalList(String dateFrom, String dateTo) throws DaoException {
//                return daoFactory.totalsBuyReturnDao().loadDataBetweenDate(dateFrom, dateTo);
                return totalBuyReturnService.getTotalBuyReturnsByDateRange(dateFrom, dateTo);
            }

            @Override
            public List<Purchase_Return> purchaseOrSalesList(int from, int to) throws DaoException {
                return purchaseReService.findBetweenTwoInvoiceNumber(from, to);
            }
        };
    }

    @Override
    public NameAndAccountInterface<Suppliers, SupplierAccount> nameAndAccountInterface() throws Exception {
        return new SupplierAndAccount(daoFactory, dataPublisher);
    }

    @Override
    public FilterDateInterface<Total_Buy_Re> filterDateInterface() {
        return null;
    }

    @Override
    public SpinnerInterface<Suppliers, SupplierAccount> spinnerInterface() {
        return null;
    }

    @Override
    public AccountData<SupplierAccount> accountData() {
        return new AccountSuppliers(daoFactory);
    }

    @Override
    public PermAccountAndNameInt permAccountAndNameInt() {
        return null;
    }

    @Override
    public void loadNameAndAccount() {
//        LoadDataAndList.get2ListSuppliers();
    }

    @Override
    public void addList(List<Total_Buy_Re> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (Total_Buy_Re totalBuy : items) {
            var listPrint = listForAllPurchase(totalBuy.getId());
            for (Purchase_Return value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                purchase.setNum(value.getInvoiceNumber());
                purchase.setName(totalBuy.getSuppliers().getName());
                purchase.setDate(totalBuy.getDate());
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

    @Override
    public InvoiceDraftService.Type draftType() {
        return InvoiceDraftService.Type.BUY_RETURN;
    }

}
