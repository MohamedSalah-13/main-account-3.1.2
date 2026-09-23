package com.hamza.account.controller.reports;

import com.hamza.controlsfx.excel.WriteExcelInterface;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The profit and loss screen's spreadsheet: one sheet holding the statement, a blank row, and the table of
 * the columns on screen - rows the screen has already written, so this only hands them over.
 *
 * @param headers the first row: the statement's column titles
 * @param rows    every row after it, each as long as it needs to be
 */
record ProfitLossExcelWriter(String sheetName, Object[] headers, List<Object[]> rows)
        implements WriteExcelInterface<Object[]> {

    ProfitLossExcelWriter {
        headers = headers.clone();
        rows = List.copyOf(rows);
    }

    @Override
    public @NotNull Object[] columnHeader() {
        return headers.clone();
    }

    @Override
    public @NotNull Object[] dataRow(Object[] row) {
        return row;
    }

    @Override
    public @NotNull List<Object[]> itemsList() {
        return rows;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public @NotNull String sheetName() {
        return sheetName;
    }
}
