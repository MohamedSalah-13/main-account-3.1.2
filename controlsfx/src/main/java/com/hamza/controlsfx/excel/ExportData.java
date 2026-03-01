package com.hamza.controlsfx.excel;

import com.hamza.controlsfx.language.Error_Text_Show;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExportData {

    /**
     * Exports a list of data items to an Excel file.
     * <p>
     * This method processes the given list of items and uses the provided
     * WriteExcelInterface to generate and save the Excel file. If the list is
     * empty, an ExcelException is thrown.
     *
     * @param <T> Type of data item in the list.
     * @param itemsList List of data items to be exported.
     * @param writeExcelInterface Interface that provides methods for Excel file generation.
     * @return An integer representing the status code of the operation. A returned value of 1 typically indicates success.
     * @throws ExcelException if an error occurs during the Excel file export.
     */
    public static <T> int exportDataToExcel(@NotNull List<T> itemsList, @NotNull WriteExcelInterface<T> writeExcelInterface) throws ExcelException {
        try {
            if (itemsList.isEmpty()) {
                throw new ExcelException(Error_Text_Show.NO_DATA);
            }
            return new SaveExcelFile().downLoadExcelFile(writeExcelInterface);
        } catch (NullPointerException e) {
            throw new ExcelException(Error_Text_Show.NO_SUCH_FILE_OR_DIRECTORY);
        } catch (Exception e) {
            throw new ExcelException(e);
        }
    }
}
