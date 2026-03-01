package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.domain.Total_Buy_Re;
import org.jetbrains.annotations.NotNull;

public class TotalsBuyReturnData implements TotalsDataInterface<Total_Buy_Re> {

    @Override
    public int getIdData(Total_Buy_Re totalBuyRe) {
        return totalBuyRe.getSuppliers().getId();
    }

    @NotNull
    @Override
    public String getNameData(Total_Buy_Re totalBuyRe) {
        return totalBuyRe.getSuppliers().getName();
    }

}
