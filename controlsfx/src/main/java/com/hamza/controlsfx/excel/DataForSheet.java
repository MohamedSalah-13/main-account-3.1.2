package com.hamza.controlsfx.excel;

import javafx.collections.ObservableList;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.jetbrains.annotations.NotNull;

public interface DataForSheet<T> {

    /**
     * Populates an ObservableList with data from a given row of an Excel sheet using a DataFormatter.
     *
     * @param observableDataList the list to which the data from the sheet row will be added
     * @param dataFormatter the formatter used to format the cell data
     * @param row the current row of the Excel sheet being processed
     */
    void dataForSheet(@NotNull ObservableList<T> observableDataList
            , @NotNull DataFormatter dataFormatter, @NotNull Row row);
}
