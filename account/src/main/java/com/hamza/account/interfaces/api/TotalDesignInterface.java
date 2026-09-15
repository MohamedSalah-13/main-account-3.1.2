package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseTotals;

/**
 * How one document family's totals screen is built. Fixed on {@link BaseTotals} - see
 * {@link TotalsDataInterface} for why the four implementations narrow with a checked cast
 * instead of naming the concrete row type here.
 * <p>
 * It used to delete the rows as well, three ways per family, two of which nothing called and
 * both of which deleted without a shift-correction reason. Deleting is
 * {@code DocumentDeletionService}'s now, for every family, on one path.
 */
public interface TotalDesignInterface extends DataTable<BaseTotals> {

    TotalsDataInterface totalsDataInterface();

}
