package com.hamza.account.controller.items;

import com.hamza.account.features.itemreports.ItemReportColumn;
import com.hamza.account.features.itemreports.ItemReportResult;
import com.hamza.account.features.itemreports.ItemReportRow;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The paper's totals strip - found by drawing the Pareto paper to an image: the labels went in the
 * wide cell and the figures in the last column's, which on that report is one letter wide - and the
 * paper itself.
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

    /**
     * The report is drawn on the paper it is handed. It was A4 on its side whatever the shop had set,
     * so on an A5 shop the direct print sent an A4 page to a printer set for A5.
     */
    @Test
    void isWrittenOnThePaperItIsHanded(@TempDir Path folder) throws Exception {
        ItemReportResult result = ItemReportResult.of(COLUMNS,
                List.of(ItemReportRow.item(0, 1, "زيت عباد الشمس")));
        File target = folder.resolve("item-report.pdf").toFile();

        assertTrue(ItemReportPdf.write(result, "تقرير", "", target.getAbsolutePath(), PageSize.A5.rotate()));

        try (PDDocument document = Loader.loadPDF(target)) {
            PDRectangle page = document.getPage(0).getMediaBox();
            assertEquals(PageSize.A5.getHeight(), page.getWidth(), 0.5f, "A5, turned on its side");
            assertEquals(PageSize.A5.getWidth(), page.getHeight(), 0.5f);
        }
    }
}
