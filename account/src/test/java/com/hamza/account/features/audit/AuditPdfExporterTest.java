package com.hamza.account.features.audit;

import com.hamza.account.features.export.PageNumbering;
import com.hamza.account.features.export.ReportLabels;
import com.hamza.account.features.export.ReportLetterhead;
import com.hamza.account.features.export.ReportSetup;
import com.hamza.account.features.export.ReportSetups;
import com.hamza.account.features.export.ReportStyle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditPdfExporterTest {

    @TempDir Path temp;

    @AfterEach
    void uninstall() {
        ReportSetups.uninstall();
    }

    private Path export(String name) throws Exception {
        AuditLogEntry row = new AuditLogEntry(8, "CUSTOM", "15", AuditAction.INSERT, 2,
                "Operator", LocalDateTime.of(2026, 9, 7, 11, 0), "", "{\"name\":\"Customer\"}",
                "APP", "machine", "Till", "created");
        LocalDate day = LocalDate.of(2026, 9, 7);
        AuditLogQuery query = new AuditLogQuery("", day, day, null, AuditActionFilter.ALL,
                "", AuditSourceFilter.ALL, AuditLogSort.NEWEST, 0, 50);
        Path target = temp.resolve(name);
        new AuditPdfExporter().export(target,
                new AuditExportDocument(query, List.of(row), LocalDateTime.now()));
        return target;
    }

    @Test
    void createsAReadablePdfArtifact() throws Exception {
        Path target = export("audit.pdf");

        assertArrayEquals("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                java.util.Arrays.copyOf(Files.readAllBytes(target), 4));
    }

    /**
     * The shop's report style reaches the audit log's paper: it drew its own page, and nothing on the
     * report appearance tab changed it.
     */
    @Test
    void isPrintedInTheShopsReportStyle() throws Exception {
        ReportStyle style = ReportStyle.DEFAULT.toBuilder().showLetterhead(true)
                .pageNumbering(PageNumbering.PAGE_OF_TOTAL).build();
        ReportSetups.install(() -> new ReportSetup(style, new ReportLetterhead("5501", List.of(), null),
                new ReportLabels("", "", "%d : %d", ""), ""));

        Path target = export("styled.pdf");

        try (PdfDocument pdf = new PdfDocument(new PdfReader(target.toString()))) {
            String page = PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertTrue(page.contains("5501"), "the shop's letterhead: " + page);
            assertTrue(page.contains("1 : 1"), "the shop's page numbers: " + page);
        }
    }
}
