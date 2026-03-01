package com.hamza.account.controller.invoice;

import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;

import java.util.HashMap;

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

public class ShowInvoiceDetails {

    public static <T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
    HashMap<String, Object> invoiceDetails(DataInterface<T1, T2, T3, T4> dataInterface, T2 t2) {
        TotalsDataInterface<T2> totalsDataInterface = dataInterface.totalDesignInterface().totalsDataInterface();

        double paid = t2.getPaid();
        double total = t2.getTotal();
        double discount = t2.getDiscount();
        double rest = roundToTwoDecimalPlaces(total - (discount + paid));
        var type = t2.getInvoiceType().getType();
        if (type == null) type = InvoiceType.CASH.getType();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put(ShowInvoiceNameData.ID, totalsDataInterface.getNum(t2));
        hashMap.put(ShowInvoiceNameData.NAME, totalsDataInterface.getNameData(t2));
        hashMap.put(ShowInvoiceNameData.DATE, t2.getDate());
        hashMap.put(ShowInvoiceNameData.STOCK, t2.getStockData().getName());
        hashMap.put(ShowInvoiceNameData.PAID, paid);
        hashMap.put(ShowInvoiceNameData.DISCOUNT, discount);
        hashMap.put(ShowInvoiceNameData.TOTAL, total);
        hashMap.put(ShowInvoiceNameData.REST, rest);
        hashMap.put(ShowInvoiceNameData.TYPE, type);
        hashMap.put(ShowInvoiceNameData.DATE_INSERT, totalsDataInterface.getDateInsert(t2));
        return hashMap;
    }
}
