package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Total_Sales_Re;

import java.util.function.ToDoubleFunction;

public class TotalsSalesReturnData implements TotalsDataInterface {

    /** Every row on a sales-return totals screen is a {@link Total_Sales_Re}. */
    private static Total_Sales_Re cast(BaseTotals t2) {
        return (Total_Sales_Re) t2;
    }

    @Override
    public Employees getDelegateData(BaseTotals t2) {
        return cast(t2).getEmployeeObject();
    }

    @Override
    public int getIdData(BaseTotals t2) {
        return cast(t2).getCustomer().getId();
    }

    @Override
    public String getNameData(BaseTotals t2) {
        return cast(t2).getCustomer().getName();
    }

    @Override
    public ToDoubleFunction<BaseTotals> getTotalProfit() {
        return t2 -> cast(t2).getTotal_profit();
    }

    @Override
    public int getSourceInvoiceNumber(BaseTotals t2) {
        return cast(t2).getSourceInvoiceNumber();
    }

    @Override
    public String getReturnReason(BaseTotals t2) {
        return cast(t2).getReturnReason();
    }
}
