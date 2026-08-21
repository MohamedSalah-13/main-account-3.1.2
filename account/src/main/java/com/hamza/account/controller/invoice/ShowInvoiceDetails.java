package com.hamza.account.controller.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;

import java.util.HashMap;

public class ShowInvoiceDetails {

    /**
     * The header of one document, for printing. Everything the per-family
     * {@link TotalsDataInterface} had to be asked for is already resolved on the view,
     * so no caller has to name the concrete totals type to build this.
     */
    public static HashMap<String, Object> invoiceDetails(InvoiceHeaderView header) {
        BaseTotals t2 = header.totals();
        double paid = t2.getPaid();
        double total = t2.getTotal();
        double discount = t2.getDiscount();
        double rest = MoneyMath.asDouble(MoneyMath.subtract(
                MoneyMath.subtract(MoneyMath.decimal(total), MoneyMath.decimal(discount)),
                MoneyMath.decimal(paid)));
        var type = t2.getInvoiceType().getType();
        if (type == null) type = InvoiceType.CASH.getType();

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put(ShowInvoiceNameData.ID, t2.getId());
        hashMap.put(ShowInvoiceNameData.NAME, header.partyName());
        hashMap.put(ShowInvoiceNameData.DATE, t2.getDate());
        hashMap.put(ShowInvoiceNameData.STOCK, t2.getStockData().getName());
        hashMap.put(ShowInvoiceNameData.PAID, paid);
        hashMap.put(ShowInvoiceNameData.DISCOUNT, discount);
        hashMap.put(ShowInvoiceNameData.TOTAL, total);
        hashMap.put(ShowInvoiceNameData.REST, rest);
        hashMap.put(ShowInvoiceNameData.TYPE, type);
        hashMap.put(ShowInvoiceNameData.DATE_INSERT, header.dateInsert());
        return hashMap;
    }
}
