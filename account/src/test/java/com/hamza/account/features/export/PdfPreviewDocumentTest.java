package com.hamza.account.features.export;

import com.itextpdf.kernel.geom.PageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A written report in the preview window: what is looked at, saved and printed is one file. */
class PdfPreviewDocumentTest {

    @TempDir
    Path dir;

    private File report() {
        File file = dir.resolve("report.pdf").toFile();
        assertTrue(new PdfExportService(ReportSetup.plain()).exportGroupedReport(file.getAbsolutePath(), "1", "",
                new String[]{"101", "202"}, new float[]{1, 1}, List.<String[]>of(new String[]{"1", "111"}), null,
                PageSize.A5.rotate()));
        return file;
    }

    @Test
    void itShowsTheFilesPagesAndSavesTheFileItself() throws Exception {
        File pdf = report();
        Path saved = dir.resolve("kept.pdf");
        try (PdfPreviewDocument document = PdfPreviewDocument.open(pdf, ReportPaperSize.A5)) {
            assertEquals(1, document.pageCount());
            assertEquals(PageSize.A5.getHeight(), document.pageWidth(0), 0.5, "sideways, so wide");
            assertEquals(Math.round(PageSize.A5.getHeight()), document.render(0, 1f).getWidth(), 1);
            assertTrue(document.canSave());
            document.saveAs(saved);
        }
        assertArrayEquals(Files.readAllBytes(pdf.toPath()), Files.readAllBytes(saved));
    }

    /** The file is the caller's: the window deletes it even when it could not be opened. */
    @Test
    void closingLeavesTheFileToWhoeverWroteIt() throws Exception {
        File pdf = report();
        PdfPreviewDocument.open(pdf, null).close();
        assertTrue(pdf.exists());
    }
}
