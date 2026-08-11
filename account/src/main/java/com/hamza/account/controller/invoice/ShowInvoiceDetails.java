package com.hamza.account.controller.invoice;

import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;

import java.util.HashMap;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public class ShowInvoiceDetails {

    /** The header of one document, for printing. Only the totals type has to be named. */
    public static <T2 extends BaseTotals>
    HashMap<String, Object> invoiceDetails(DataInterface<?, T2, ?, ?> dataInterface, T2 t2) {
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
