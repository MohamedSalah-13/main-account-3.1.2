package com.hamza.account.features.report.itemsales;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the item sales report reads. */
public interface ItemSalesRepository {

    /**
     * One row per item named on a sale or a sales return dated in the period and matching the text.
     *
     * @param withCost whether the lines' cost is read at all; without it every row's cost is {@code null}
     */
    List<ItemSalesRow> rows(ItemSalesFilter filter, boolean withCost) throws DaoException;

    /** The sales' own discounts less the returns', over the period - the figure that belongs to no item. */
    BigDecimal headerDiscounts(LocalDate from, LocalDate to) throws DaoException;

    /** One item's lines over the period, by unit and price. */
    List<ItemSalesLine> lines(int itemId, LocalDate from, LocalDate to) throws DaoException;
}
