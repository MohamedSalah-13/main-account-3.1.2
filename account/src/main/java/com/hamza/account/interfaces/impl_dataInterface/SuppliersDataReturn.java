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
import com.hamza.account.interfaces.impl_account.AccountSuppliers;
import com.hamza.account.interfaces.impl_invoiceBuy.PurchaseInvoiceReturn;
import com.hamza.account.interfaces.impl_namesDao.SupplierAndAccount;
import com.hamza.account.interfaces.impl_totalDesgin.TotalsPurchaseReturnImplDesign;
import com.hamza.account.interfaces.names.SupplierName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.service.PurchaseReService;
import com.hamza.account.service.TotalBuyReturnService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.DaoList;

import java.util.List;

public class SuppliersDataReturn extends LoadData implements DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> {

    private final TotalBuyReturnService totalBuyReturnService = ServiceRegistry.get(TotalBuyReturnService.class);
    private final PurchaseReService purchaseReService = ServiceRegistry.get(PurchaseReService.class);

    private final DesignInterface designInterface = () -> DocumentType.PURCHASE_RETURN;

    private final TotalDesignInterface totalDesignInterface = new TotalsPurchaseReturnImplDesign(totalBuyReturnService);

    private final InvoiceBuy<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> invoiceBuy = new PurchaseInvoiceReturn();

    private final NameData<Suppliers> nameData = new SupplierName();

    private final NameAndAccountInterface<Suppliers, SupplierAccount> nameAndAccountInterface = new SupplierAndAccount();

    private final AccountData<SupplierAccount> accountData = new AccountSuppliers(daoFactory);

    private final TotalsAndPurchaseList<Purchase_Return, Total_Buy_Re> totalsAndPurchaseList = new TotalsAndPurchaseList<>() {
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

        @Override
        public List<Total_Buy_Re> searchTotals(TotalsSearchCriteria criteria) throws DaoException {
            return totalBuyReturnService.searchTotals(criteria);
        }
    };

    public SuppliersDataReturn(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
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
        return InvoiceSide.PURCHASE;
    }

    @Override
    public List<Purchase_Return> listForAllPurchase(int i) throws DaoException {
//        return daoFactory.purchaseReturnsDao().loadAllById(i);
        return purchaseReService.fetchByInvoiceNumber(i);
    }

    @Override
    public InvoiceBuy<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> invoiceBuy() {
        return invoiceBuy;
    }

    @Override
    public NameData<Suppliers> nameData() {
        return nameData;
    }

    @Override
    public TotalsAndPurchaseList<Purchase_Return, Total_Buy_Re> totalsAndPurchaseList() {
        return totalsAndPurchaseList;
    }

    @Override
    public NameAndAccountInterface<Suppliers, SupplierAccount> nameAndAccountInterface() throws Exception {
        return nameAndAccountInterface;
    }

    @Override
    public AccountData<SupplierAccount> accountData() {
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
        InvoiceSaveService<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> invoiceSaveService =
                new InvoiceSaveService<>(invoiceBuy, totalsAndPurchaseList,
                        designInterface.documentType(),
                        ServiceRegistry.get(TreasuryService.class)::getTreasuryByName,
                        ServiceRegistry.get(EmployeeService.class)::getDelegateByName);
        return invoiceSaveService.save(command);
    }

    @Override
    public void addList(List<? extends BaseTotals> items, List<PrintPurchaseWithName> printPurchaseWithNames) throws DaoException {
        for (BaseTotals row : items) {
            Total_Buy_Re totalBuy = (Total_Buy_Re) row;
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
}
