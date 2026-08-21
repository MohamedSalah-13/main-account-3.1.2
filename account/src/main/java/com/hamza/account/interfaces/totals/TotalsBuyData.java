package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Total_buy;

public class TotalsBuyData implements TotalsDataInterface {

    /** Every row on a purchases totals screen is a {@link Total_buy}. */
    private static Total_buy cast(BaseTotals t2) {
        return (Total_buy) t2;
    }

    @Override
    public int getIdData(BaseTotals t2) {
        return cast(t2).getSupplierData().getId();
    }

    @Override
    public String getNameData(BaseTotals t2) {
        return cast(t2).getSupplierData().getName();
    }

}
