package com.hamza.account.controller.others;

import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

public interface ReturnsItemsInterface<T> {
    /**
     * Adds a column to the provided TableView instance.
     *
     * @param tableView the TableView instance to which the column will be added
     */
    void addColumn(TableView<T> tableView);

    /**
     * Returns the class type of the generic parameter T.
     *
     * @return the class type of the generic parameter T
     */
    @NotNull Class<T> objectClass();

    /**
     * Retrieves a list of table items of type T.
     *
     * @return a non-null list of items of type T
     * @throws DaoException if there is an error accessing the data source
     */
    @NotNull List<T> tableList() throws DaoException;

    /**
     * Retrieves a list of invoice numbers.
     *
     * @return a list of integers representing invoice numbers
     * @throws DaoException if there is an error accessing the data source
     */
    @NotNull List<Integer> invoiceNumberList() throws DaoException;

    /**
     * Filters items by the given invoice number.
     *
     * @param i the invoice number to filter items by
     * @return a predicate that tests if an item matches the given invoice number
     */
    @NotNull Predicate<T> filterByInvoiceNumber(int i);
}
