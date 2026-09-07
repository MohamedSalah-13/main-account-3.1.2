package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AuditPdfExporterTest {

    @TempDir Path temp;

    @Test
    void createsAReadablePdfArtifact() throws Exception {
        AuditLogEntry row = new AuditLogEntry(8, "CUSTOM", "15", AuditAction.INSERT, 2,
                "Operator", LocalDateTime.of(2026, 9, 7, 11, 0), "", "{\"name\":\"Customer\"}",
                "APP", "machine", "Till", "created");
        LocalDate day = LocalDate.of(2026, 9, 7);
        AuditLogQuery query = new AuditLogQuery("", day, day, null, AuditActionFilter.ALL,
                "", AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50);
        Path target = temp.resolve("audit.pdf");

        new AuditPdfExporter().export(target,
                new AuditExportDocument(query, List.of(row), LocalDateTime.now()));

        assertArrayEquals("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                java.util.Arrays.copyOf(Files.readAllBytes(target), 4));
    }
}
