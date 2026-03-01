package com.hamza.account.controller.invoice;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.interfaces.api.*;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.NameService;
import com.hamza.controlsfx.observer.Publisher;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

public class BuyData<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends ServiceData {

    protected final InvoiceBuy<T1, T2, T3, T4> invoiceBuy;
    protected final DataInterface<T1, T2, T3, T4> dataInterface;
    protected final DesignInterface designInterface;
    protected final Publisher<ItemsModel> publisherAddItem;
    protected final Publisher<String> publisherBuy;
    protected final int num_invoice_update;
    protected final TotalsAndPurchaseList<T1, T2> totalsAndPurchaseList;
    protected final PurchaseSalesInterface purchaseSalesInterface;
    protected final BooleanProperty checkTableForZeroBalanceOrPriceBoolean = new SimpleBooleanProperty(false);
    protected final NameData<T3> t3NameData;
    protected final AccountData<T4> accountData;
    protected final NameService<T3> nameService;
    protected final NameAndAccountInterface<T3, T4> nameAndAccountInterface;
//    protected int numItem;

    public BuyData(DataInterface<T1, T2, T3, T4> dataInterface
            , DataPublisher dataPublisher, DaoFactory daoFactory
            , int numInvoiceUpdate) throws Exception {
        super(daoFactory);
        this.dataInterface = dataInterface;
        this.publisherBuy = dataInterface.publisherPurchaseOrSales();
        this.publisherAddItem = dataPublisher.getPublisherAddItem();
        this.num_invoice_update = numInvoiceUpdate;
        this.designInterface = dataInterface.designInterface();
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.t3NameData = dataInterface.nameData();
        this.totalsAndPurchaseList = dataInterface.totalsAndPurchaseList();
        this.purchaseSalesInterface = dataInterface.purchaseSalesInterface();
        this.nameAndAccountInterface = dataInterface.nameAndAccountInterface();
        this.accountData = dataInterface.accountData();
        this.nameService = new NameService<>(t3NameData);

    }

    /**
     * Checks if any element in the provided list has a zero price or quantity.
     *
     * @param list the list of items to be checked
     * @return true if any item has a price or quantity of zero, false otherwise
     */
    protected boolean checkTableForZeroBalanceOrPrice(List<T1> list) {
        List<Double> doubleList = new ArrayList<>();
        for (T1 t1 : list) {
            double price1 = purchaseSalesInterface.getPrice(t1);
            double quantity1 = purchaseSalesInterface.getQuantity(t1);
            doubleList.add(price1);
            doubleList.add(quantity1);
        }
        Optional<Double> first = doubleList.stream().filter(aDouble -> aDouble <= 0).findFirst();
//        first.ifPresentOrElse(aDouble -> checkTableForZeroBalanceOrPriceBoolean.set(true), () -> checkTableForZeroBalanceOrPriceBoolean.set(false));
        return first.isPresent();
    }

    /**
     * Calculates the sum of values from a list of T1 elements based on a provided function.
     *
     * @param function a ToDoubleFunction that extracts the double value from a T1 element
     * @param list     a List of T1 elements to be summed
     * @return the sum of double values extracted from the list
     */
    protected double getSumBuyFunction(ToDoubleFunction<T1> function, List<T1> list) {
        return roundToTwoDecimalPlaces(list.stream().mapToDouble(function).sum());
    }

}
