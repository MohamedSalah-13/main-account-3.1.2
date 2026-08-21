package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseTotals;
import org.jetbrains.annotations.NotNull;

/**
 * How one document family's totals screen is built and its rows deleted. Fixed on
 * {@link BaseTotals} - see {@link TotalsDataInterface} for why the four implementations
 * narrow with a checked cast instead of naming the concrete row type here.
 */
public interface TotalDesignInterface extends DataTable<BaseTotals> {

    TotalsDataInterface totalsDataInterface();

    int deleteData(int id) throws Exception;

    int deleteMultiData(@NotNull Integer... ids) throws Exception;

}
