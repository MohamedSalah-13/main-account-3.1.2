package com.hamza.controlsfx.excel;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveExcelFileTest {

    private static WriteExcelInterface<Integer> writer(List<Integer> items, boolean addData) {
        return new WriteExcelInterface<>() {
            @Override
            public @NotNull Object[] columnHeader() {
                return new Object[]{"number"};
            }

            @Override
            public @NotNull Object[] dataRow(Integer item) {
                return new Object[]{item};
            }

            @Override
            public @NotNull List<Integer> itemsList() {
                return items;
            }

            @Override
            public boolean addDataToFile() {
                return addData;
            }

            @Override
            public @NotNull String sheetName() {
                return "sheet";
            }
        };
    }

    @Test
    void rowsKeepTheOrderTheyWereGivenIn() {
        // Twenty-five is past the point where a row number sorted as a string reorders the
        // file: "10" comes before "2".
        List<Integer> items = IntStream.rangeClosed(1, 25).boxed().toList();

        List<Object[]> rows = SaveExcelFile.sheetRows(writer(items, true));

        assertEquals(26, rows.size());
        assertEquals("number", rows.getFirst()[0]);
        for (int index = 1; index <= 25; index++) {
            assertEquals(index, rows.get(index)[0]);
        }
    }

    @Test
    void aWriterThatAddsNoDataWritesOnlyTheHeader() {
        List<Object[]> rows = SaveExcelFile.sheetRows(writer(List.of(1, 2, 3), false));

        assertEquals(1, rows.size());
        assertEquals("number", rows.getFirst()[0]);
    }
}
