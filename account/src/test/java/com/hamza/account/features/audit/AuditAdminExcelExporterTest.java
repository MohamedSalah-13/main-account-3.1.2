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

class AuditAdminExcelExporterTest {

    @TempDir Path temp;

    @Test
    void writesTheCompleteAdministrationEvidence() throws Exception {
        Path target = temp.resolve("administration.xlsx");

        new AuditAdminExcelExporter().export(target,
                new AuditAdminExportDocument(query(), List.of(row()), LocalDateTime.now()));

        assertTrue(Files.size(target) > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(target))) {
            assertEquals("7", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("requested", workbook.getSheetAt(0).getRow(1).getCell(7).getStringCellValue());
            assertEquals("{\"format\":\"EXCEL\"}",
                    workbook.getSheetAt(0).getRow(1).getCell(8).getStringCellValue());
        }
    }

    private static AuditAdminEventQuery query() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        return new AuditAdminEventQuery("", day, day, null, "", AuditSourceFilter.ALL,
                AuditAdminSort.NEWEST, 0, 50);
    }

    private static AuditAdminEvent row() {
        return new AuditAdminEvent(7, "EXPORT", 2, "security", "APP", "machine-1", "Till 1",
                LocalDateTime.of(2026, 9, 7, 12, 30), "requested", 3,
                "{\"format\":\"EXCEL\"}");
    }
}
