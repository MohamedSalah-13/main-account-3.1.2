package com.hamza.account.interfaces.totals;

import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Total_Buy_Re;
import org.jetbrains.annotations.NotNull;

public class TotalsBuyReturnData implements TotalsDataInterface {

    /** Every row on a purchase-return totals screen is a {@link Total_Buy_Re}. */
    private static Total_Buy_Re cast(BaseTotals t2) {
        return (Total_Buy_Re) t2;
    }

    @Override
    public int getIdData(BaseTotals t2) {
        return cast(t2).getSuppliers().getId();
    }

    @NotNull
    @Override
    public String getNameData(BaseTotals t2) {
        return cast(t2).getSuppliers().getName();
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
