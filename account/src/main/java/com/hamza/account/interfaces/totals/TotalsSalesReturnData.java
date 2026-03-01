package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Total_Sales_Re;

import java.util.function.ToDoubleFunction;

public class TotalsSalesReturnData implements TotalsDataInterface<Total_Sales_Re> {

    @Override
    public Employees getDelegateData(Total_Sales_Re t2) {
        return t2.getEmployeeObject();
    }

    @Override
    public int getIdData(Total_Sales_Re totalSalesRe) {
        return totalSalesRe.getCustomer().getId();
    }

    @Override
    public String getNameData(Total_Sales_Re totalSalesRe) {
        return totalSalesRe.getCustomer().getName();
    }

    @Override
    public ToDoubleFunction<Total_Sales_Re> getTotalProfit() {
        return Total_Sales_Re::getTotal_profit;
    }

}
