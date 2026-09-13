package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import org.apache.pdfbox.Loader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftPeriodExportServiceTest {

    @TempDir Path directory;

    @Test
    void exportsTheSameAggregatedRowsToExcelAndPdf() throws Exception {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "manager", List.of(AppPermissions.USER_SHIFT_MANAGE));
        ServiceRegistry.register(UserSessionContext.class, session);
        ShiftPeriodReport report = new ShiftPeriodReport(
                new ShiftPeriodQuery(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 13), null, null),
                List.of(new ShiftPeriodRow(2, "cashier", 3, "drawer", 4, 1, 3,
                        new BigDecimal("100"), BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90"),
                        new BigDecimal("89"), new BigDecimal("-1"), 5)));
        ShiftPeriodExportService service = new ShiftPeriodExportService();
        Path excel = directory.resolve("shifts.xlsx");
        Path pdf = directory.resolve("shifts.pdf");

        service.exportExcel(excel, report);
        service.exportPdf(pdf, report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertEquals(2, workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals("cashier", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("-1", workbook.getSheetAt(0).getRow(1).getCell(12).getStringCellValue());
        }
        try (var document = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, document.getNumberOfPages());
        }
        assertTrue(Files.size(pdf) > 1_000);
    }
}
