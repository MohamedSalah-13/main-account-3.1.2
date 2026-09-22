package com.hamza.account.controller.items;

import com.hamza.account.features.itemreports.ItemReportColumn;
import com.hamza.account.features.itemreports.ItemReportResult;
import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The paper's totals strip. Found by drawing the Pareto paper to an image: the labels went in the
 * wide cell and the figures in the last column's, which on that report is one letter wide.
 */
class ItemReportPdfTest {

    private static final List<ItemReportColumn> COLUMNS = List.of(ItemReportColumn.name("itemreport.column.name"));

    @Test
    void severalTotalsKeepEachFigureBesideItsLabel() {
        ItemReportResult result = ItemReportResult.of(COLUMNS, List.of(), List.of(
                new ItemReportResult.Total("itemreport.pareto.total.class.a", "3 / 10"),
                new ItemReportResult.Total("itemreport.pareto.total.items.net", "1,000.00")));
        LanguageManager language = LanguageManager.getInstance();

        assertArrayEquals(new String[]{
                language.getString("itemreport.pareto.total.class.a") + ": 3 / 10  |  "
                        + language.getString("itemreport.pareto.total.items.net") + ": 1,000.00", ""},
                ItemReportPdf.totalsLine(result));
    }

    @Test
    void oneTotalKeepsItsOwnCell() {
        ItemReportResult result = ItemReportResult.of(COLUMNS, List.of(), List.of(
                new ItemReportResult.Total("itemreport.pareto.total.items.net", "1,000.00")));

        assertArrayEquals(new String[]{
                LanguageManager.getInstance().getString("itemreport.pareto.total.items.net"), "1,000.00"},
                ItemReportPdf.totalsLine(result));
    }

    @Test
    void noTotalsNoStrip() {
        assertNull(ItemReportPdf.totalsLine(ItemReportResult.of(COLUMNS, List.of())));
    }
}
