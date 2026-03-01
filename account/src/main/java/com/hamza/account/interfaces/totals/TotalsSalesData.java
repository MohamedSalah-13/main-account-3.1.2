package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Total_Sales;

import java.util.function.ToDoubleFunction;

public class TotalsSalesData implements TotalsDataInterface<Total_Sales> {
    @Override
    public Employees getDelegateData(Total_Sales t2) {
        return t2.getEmployeeObject();
    }

    @Override
    public int getIdData(Total_Sales totalSales) {
        return totalSales.getCustomers().getId();
    }

    @Override
    public String getNameData(Total_Sales totalSales) {
        return totalSales.getCustomers().getName();
    }

    @Override
    public ToDoubleFunction<Total_Sales> getTotalProfit() {
        return Total_Sales::getTotal_profit;
    }

}
