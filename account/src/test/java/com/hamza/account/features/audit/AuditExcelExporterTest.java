package com.hamza.account.features.audit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditExcelExporterTest {

    @TempDir Path temp;

    @Test
    void writesTheCompleteFilteredRowIncludingBeforeAndAfterValues() throws Exception {
        AuditLogEntry row = new AuditLogEntry(42, "ITEMS", "7", AuditAction.UPDATE, 3,
                "Operator", LocalDateTime.of(2026, 9, 7, 12, 30), "{\"old\":1}",
                "{\"new\":2}", "APP", "machine-1", "Till 1", "price correction");
        LocalDate today = LocalDate.of(2026, 9, 7);
        AuditLogQuery query = new AuditLogQuery("", today, today, null, AuditActionFilter.ALL,
                "", AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50);
        Path target = temp.resolve("audit.xlsx");

        new AuditExcelExporter().export(target,
                new AuditExportDocument(query, List.of(row), LocalDateTime.now()));

        assertTrue(Files.size(target) > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            assertEquals("42", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("{\"old\":1}", workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
            assertEquals("{\"new\":2}", workbook.getSheetAt(0).getRow(1).getCell(10).getStringCellValue());
        }
    }
}
