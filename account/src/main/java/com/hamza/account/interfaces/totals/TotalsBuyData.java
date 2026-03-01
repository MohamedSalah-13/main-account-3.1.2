package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.domain.Total_buy;

public class TotalsBuyData implements TotalsDataInterface<Total_buy> {

    @Override
    public int getIdData(Total_buy totalBuy) {
        return totalBuy.getSupplierData().getId();
    }

    @Override
    public String getNameData(Total_buy totalBuy) {
        return totalBuy.getSupplierData().getName();
    }

}
