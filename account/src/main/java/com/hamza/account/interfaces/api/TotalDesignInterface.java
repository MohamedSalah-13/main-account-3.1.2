package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

public interface TotalDesignInterface<T extends BaseTotals> extends DataTable<T> {

    TotalsDataInterface<T> totalsDataInterface();

    int deleteData(T t2) throws Exception;

    int deleteMultiData(@NotNull Integer... ids) throws Exception;

    Predicate<T> filterById(int id);

    Predicate<T> filterByName(String name);

    Predicate<T> filterByDelegate(String name);

    default Predicate<T> filterByInvoiceType(InvoiceType type) {
        return null;
    }

    WriteExcelInterface<T> writeExcelInterface(List<T> items);

}
