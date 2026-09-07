package com.hamza.account.features.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AuditAdminPdfExporterTest {

    @TempDir Path temp;

    @Test
    void createsAReadableAdministrationPdf() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 7);
        AuditAdminEventQuery query = new AuditAdminEventQuery("", day, day, null, "",
                AuditSourceFilter.ALL, AuditAdminSort.NEWEST, 0, 50);
        AuditAdminEvent row = new AuditAdminEvent(7, "DELETE_SELECTED", 2, "security", "APP",
                "machine-1", "Till 1", LocalDateTime.of(2026, 9, 7, 12, 30),
                "cleanup request", 3, "{\"ids\":[1,2,3]}");
        Path target = temp.resolve("administration.pdf");

        new AuditAdminPdfExporter().export(target,
                new AuditAdminExportDocument(query, List.of(row), LocalDateTime.now()));

        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII),
                Arrays.copyOf(Files.readAllBytes(target), 4));
    }
}
