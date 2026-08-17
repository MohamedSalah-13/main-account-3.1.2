package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TotalDesignInterface<T extends BaseTotals> extends DataTable<T> {

    TotalsDataInterface<T> totalsDataInterface();

    int deleteData(T t2) throws Exception;

    int deleteMultiData(@NotNull Integer... ids) throws Exception;

    WriteExcelInterface<T> writeExcelInterface(List<T> items);

}
