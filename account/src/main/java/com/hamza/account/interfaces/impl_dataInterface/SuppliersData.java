package com.hamza.account.interfaces.impl_dataInterface;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.model.PrintPurchaseWithName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.event.EventType;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.interfaces.impl_design.DesignSuppliers;
import com.hamza.account.interfaces.impl_invoiceBuy.PurchaseInvoice;
import com.hamza.account.interfaces.impl_namesDao.SupplierAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalsPurchaseImplDesign;
import com.hamza.account.interfaces.names.SupplierName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.account.service.PurchaseService;
import com.hamza.account.service.TotalBuyService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;

public class SuppliersData
        extends LoadData implements DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> {

    private final TotalBuyService totalBuyService = ServiceRegistry.get(TotalBuyService.class);
    private final PurchaseService purchaseService = ServiceRegistry.get(PurchaseService.class);

    public SuppliersData(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    @Override
    public DesignInterface designInterface() {
        return new DesignSuppliers();
    }

    @Override
    public TotalDesignInterface<Total_buy> totalDesignInterface() {
        return new TotalsPurchaseImplDesign(totalBuyService, this);
    }

    @Override
    public List<Purchase> listForAllPurchase(int i) throws DaoException {
//        return daoFactory.purchaseDao().loadAllById(i);
        return purchaseService.fetchByInvoiceNumber(i);
    }

    @Override
    public InvoiceBuy<Purchase, Total_buy, Suppliers, SupplierAccount> invoiceBuy() {
        return new PurchaseInvoice();
    }

    @Override
    public NameData<Suppliers> nameData() {
        return new SupplierName();
    }

    @Override
    public TotalsAndPurchaseList<Purchase, Total_buy> totalsAndPurchaseList() {
        return new TotalsAndPurchaseList<>() {
            @Override
            public DaoList<Total_buy> totalDao() {
                return daoFactory.totalsPurchaseDao();
            }

            @Override
            public List<Total_buy> totalList(String dateFrom, String dateTo) throws DaoException {
//                return daoFactory.totalsPurchaseDao().loadDataBetweenDate(dateFrom, dateTo);
                return totalBuyService.getTotalPurchaseByDateRange(dateFrom, dateTo);
            }

            @Override
            public List<Purchase> purchaseOrSalesList(int from, int to) throws DaoException {
                return purchaseService.findBetweenTwoInvoiceNumber(from, to);
            }

            @Override
            public int getMaxId() throws Exception {
                return totalBuyService.getMaxId();
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
    public void addList(List<Total_buy> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (Total_buy totalBuy : items) {
            var listPrint = listForAllPurchase(totalBuy.getId());
            for (Purchase value : listPrint) {
                PrintPurchaseWithName purchase = new PrintPurchaseWithName();
                purchase.setNum(value.getInvoiceNumber());
                purchase.setName(totalBuy.getSupplierData().getName());
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
