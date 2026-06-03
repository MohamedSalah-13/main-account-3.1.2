package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.event.EventType;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.interfaces.impl_design.DesignSuppliersReturn;
import com.hamza.account.interfaces.impl_invoiceBuy.PurchaseInvoiceReturn;
import com.hamza.account.interfaces.impl_namesDao.SupplierAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalsPurchaseReturnImplDesign;
import com.hamza.account.interfaces.names.SupplierName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.service.PurchaseReService;
import com.hamza.account.service.TotalBuyReturnService;
import com.hamza.account.database.DaoException;
import com.hamza.account.database.DaoList;

import java.util.List;

public class SuppliersDataReturn extends LoadData implements DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> {

    private final TotalBuyReturnService totalBuyReturnService = ServiceRegistry.get(TotalBuyReturnService.class);
    private final PurchaseReService purchaseReService = ServiceRegistry.get(PurchaseReService.class);

    public SuppliersDataReturn(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return new DesignSuppliersReturn();
    }

    @Override
    public TotalDesignInterface<Total_Buy_Re> totalDesignInterface() {
        return new TotalsPurchaseReturnImplDesign(this, totalBuyReturnService);
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

            @Override
            public int getMaxId() throws Exception {
                return totalBuyReturnService.getMaxId();
            }
        };
    }

    @Override
    public NameAndAccountInterface<Suppliers, SupplierAccount> nameAndAccountInterface() throws Exception {
        return new SupplierAndAccount(dataPublisher);
    }

    @Override
    public AccountData<SupplierAccount> accountData() {
        return new AccountSuppliers(daoFactory);
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
    public EventType getEventType() {
        return EventType.PURCHASE_INVOICE;
    }
}
