package com.hamza.controlsfx.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public interface ReadExcelInterface<T> {

    /**
     * Returns a File object that represents the path of the file to be read.
     *
     * @return a File object indicating the path of the Excel file to be read
     */
    @NotNull File thePathOfTheFileToBeRead();

    /**
     * Processes a sequence of Excel cells using the provided cell iterator and data formatter.
     *
     * @param cellIterator an iterator over the cells in a row
     * @param dataFormatter a data formatter to format cell values
     */
    void action(Iterator<Cell> cellIterator, DataFormatter dataFormatter);

    /**
     * Adds the lists contained in the given HashMap to an internal structure.
     *
     * @param listHashMap a HashMap where the key is of type T and the value is a List of any type
     */
    void addLists(HashMap<T, List<?>> listHashMap);

    /**
     * Determines whether to skip the first row of the Excel sheet.
     * This is useful when the first row contains header information rather than data.
     *
     * @return true if the first row should be skipped, false otherwise
     */
    default boolean skipFirstRow() {
        return true;
    }

    /**
     * Determines if only rows within a specified range should be processed.
     *
     * @return true if only rows between specified minimum and maximum row indices should be processed, false otherwise
     */
    default boolean betweenRows() {
        return false;
    }

    /**
     * Returns the index of the sheet to be read from the Excel file.
     *
     * @return the index of the sheet, default is 0
     */
    default int indexOfSheet() {
        return 0;
    }

    /**
     * Returns the minimum row index to be processed in the Excel file.
     *
     * @return the minimum row index
     */
    default int minRow() {
        return 0;
    }

    /**
     * Specifies the maximum row number to be considered during the Excel sheet processing.
     *
     * @return an integer representing the maximum row number
     */
    default int maxRow() {
        return 0;
    }
}
