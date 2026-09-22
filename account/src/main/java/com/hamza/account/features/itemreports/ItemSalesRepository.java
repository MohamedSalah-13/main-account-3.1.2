package com.hamza.account.features.itemreports;

import com.hamza.account.features.items.ItemCatalogFilter;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** What the items sold over a period - read for the Pareto reports. */
public interface ItemSalesRepository {

    /** One fact per item that appeared on a sale or a sales return dated in the period and matches the filter. */
    List<ItemSalesFact> sales(ItemCatalogFilter filter, LocalDate from, LocalDate to) throws DaoException;

    /**
     * The sales' own discounts less the sales returns' own discounts over the period - the figure that
     * belongs to no item and turns the items' total into the invoices'.
     */
    double headerDiscounts(LocalDate from, LocalDate to) throws DaoException;
}
