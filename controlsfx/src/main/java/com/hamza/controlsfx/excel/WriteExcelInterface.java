package com.hamza.controlsfx.excel;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface WriteExcelInterface<T> {

    /**
     * Retrieves the column headers to be used in the Excel sheet.
     *
     * @return An array of Objects representing the column headers.
     */
    @NotNull Object[] columnHeader();

    /**
     * Converts the given data item into an array of Objects representing a row in an Excel sheet.
     *
     * @param t The data item to be converted into a row.
     * @return An array of Objects representing the row data.
     */
    @NotNull Object[] dataRow(T t);

    /**
     * Retrieves a list of data items to be included in the Excel file.
     *
     * @return a list containing the data items of type T
     */
    @NotNull List<T> itemsList();

    /**
     * Adds data to the file and returns the status of the operation.
     *
     * @return true if the data was successfully added to the file, false otherwise
     */
    boolean addDataToFile();

    /**
     * Retrieves the name of the Excel sheet to be used.
     *
     * @return A string representing the name of the Excel sheet.
     */
    @NotNull String sheetName();

}
