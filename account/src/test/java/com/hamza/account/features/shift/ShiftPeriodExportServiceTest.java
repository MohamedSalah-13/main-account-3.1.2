package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import org.apache.pdfbox.Loader;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftPeriodExportServiceTest {

    @TempDir Path directory;

    private final ShiftPeriodExportService service = new ShiftPeriodExportService();

    @BeforeEach
    void signIn() {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "manager", List.of(AppPermissions.USER_SHIFT_MANAGE));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    @AfterEach
    void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    @Test
    void exportsTheSameAggregatedRowsToExcelAndPdf() throws Exception {
        ShiftPeriodReport report = report(4, 1, 3, 5);
        Path excel = directory.resolve("shifts.xlsx");
        Path pdf = directory.resolve("shifts.pdf");

        service.exportExcel(excel, report);
        service.exportPdf(pdf, report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            var row = workbook.getSheetAt(0).getRow(1);
            assertEquals(2, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("cashier", row.getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, row.getCell(12).getCellType(), "an amount a spreadsheet can sum");
            assertEquals(-1.25, row.getCell(12).getNumericCellValue());
            assertEquals("#,##0.00", row.getCell(12).getCellStyle().getDataFormatString());
            assertEquals(5, row.getCell(13).getNumericCellValue());
        }
        try (var document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void amountsArePrintedTheWayTheScreenWritesMoney() {
        String[] row = ShiftPeriodExportService.pdfRows(report(4, 1, 3, 5)).getFirst();

        assertEquals("12,500.50", row[5]);
        assertEquals("4", row[2]);
        assertEquals("5", row[13]);
    }

    /** A heading is a column title, not the summary label it was borrowed from. */
    @Test
    void noColumnHeadingCarriesALabelsColon() {
        for (String key : ShiftPeriodExportService.HEADERS) {
            String heading = LanguageManager.getInstance().getString(key);
            assertFalse(heading.strip().endsWith(":"), key + " reads \"" + heading + "\"");
        }
    }

    /**
     * The first logical column prints on the right. The report used to be drawn by a table of
     * its own that did not reverse the columns, so the user's name sat on the left and the
     * invoice count on the right - correct text, wrong page. Digits only: the Arabic font draws
     * digits, and a Latin letter may not be in it.
     */
    @Test
    void theColumnsRunRightToLeft() throws Exception {
        Path pdf = directory.resolve("order.pdf");
        service.exportPdf(pdf, report(401, 402, 403, 414));

        Map<String, Float> x = positions(pdf, Set.of("401", "402", "403", "414"));
        for (String text : List.of("401", "402", "403", "414")) {
            assertNotNull(x.get(text), text + " was not found on the page: " + x);
        }
        assertTrue(x.get("401") > x.get("402") && x.get("402") > x.get("403") && x.get("403") > x.get("414"),
                "shifts, open, closed and invoices should run right to left: " + x);
    }

    private static ShiftPeriodReport report(long shifts, long open, long closed, long invoices) {
        return new ShiftPeriodReport(
                new ShiftPeriodQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13), null, null),
                List.of(new ShiftPeriodRow(2, "cashier", 3, "drawer", shifts, open, closed,
                        new BigDecimal("12500.5"), BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90"),
                        new BigDecimal("88.75"), new BigDecimal("-1.25"), invoices)));
    }

    private static Map<String, Float> positions(Path pdf, Set<String> wanted) throws Exception {
        Map<String, Float> found = new HashMap<>();
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf.toString()))) {
            new PdfCanvasProcessor(new IEventListener() {
                @Override
                public void eventOccurred(IEventData data, EventType type) {
                    if (type == EventType.RENDER_TEXT) {
                        String text = ((TextRenderInfo) data).getText();
                        if (text != null && wanted.contains(text.strip())) {
                            found.put(text.strip(), ((TextRenderInfo) data).getBaseline().getStartPoint().get(0));
                        }
                    }
                }

                @Override
                public Set<EventType> getSupportedEvents() {
                    return Set.of(EventType.RENDER_TEXT);
                }
            }).processPageContent(document.getPage(1));
        }
        return found;
    }
}
