package com.hamza.account.controller.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.InvoiceHeaderView;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;

import java.time.LocalDateTime;
import java.util.HashMap;

public class ShowInvoiceDetails {

    /**
     * The same header, for a screen that no longer names the totals type - everything
     * the per-family interface had to be asked for is already resolved on the view.
     */
    public static HashMap<String, Object> invoiceDetails(InvoiceHeaderView header) {
        return details(header.totals(), header.partyName(), header.dateInsert());
    }

    /** The header of one document, for printing. Only the totals type has to be named. */
    public static <T2 extends BaseTotals>
    HashMap<String, Object> invoiceDetails(DataInterface<?, T2, ?, ?> dataInterface, T2 t2) {
        TotalsDataInterface<T2> totalsDataInterface = dataInterface.totalDesignInterface().totalsDataInterface();
        return details(t2, totalsDataInterface.getNameData(t2),
                totalsDataInterface.getDateInsert(t2));
    }

    private static HashMap<String, Object> details(BaseTotals t2, String partyName,
                                                   LocalDateTime dateInsert) {
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
        hashMap.put(ShowInvoiceNameData.NAME, partyName);
        hashMap.put(ShowInvoiceNameData.DATE, t2.getDate());
        hashMap.put(ShowInvoiceNameData.STOCK, t2.getStockData().getName());
        hashMap.put(ShowInvoiceNameData.PAID, paid);
        hashMap.put(ShowInvoiceNameData.DISCOUNT, discount);
        hashMap.put(ShowInvoiceNameData.TOTAL, total);
        hashMap.put(ShowInvoiceNameData.REST, rest);
        hashMap.put(ShowInvoiceNameData.TYPE, type);
        hashMap.put(ShowInvoiceNameData.DATE_INSERT, dateInsert);
        return hashMap;
    }
}
