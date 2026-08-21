package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Total_Sales;

import java.util.function.ToDoubleFunction;

public class TotalsSalesData implements TotalsDataInterface {

    /** Every row on a sales totals screen is a {@link Total_Sales}; see the interface. */
    private static Total_Sales cast(BaseTotals t2) {
        return (Total_Sales) t2;
    }

    @Override
    public Employees getDelegateData(BaseTotals t2) {
        return cast(t2).getEmployeeObject();
    }

    @Override
    public int getIdData(BaseTotals t2) {
        return cast(t2).getCustomers().getId();
    }

    @Override
    public String getNameData(BaseTotals t2) {
        return cast(t2).getCustomers().getName();
    }

    @Override
    public ToDoubleFunction<BaseTotals> getTotalProfit() {
        return t2 -> cast(t2).getTotal_profit();
    }

}
