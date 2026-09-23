package com.hamza.account.features.export;

import com.itextpdf.kernel.geom.PageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preview's pages, drawn from a report the real renderer wrote. */
class PdfPageRendererTest {

    @TempDir
    Path dir;

    private File report(String name, PageSize size, int rows) {
        List<String[]> lines = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            lines.add(new String[]{String.valueOf(i), "111"});
        }
        File file = dir.resolve(name).toFile();
        assertTrue(new PdfExportService(ReportSetup.plain()).exportGroupedReport(file.getAbsolutePath(), "1", "",
                new String[]{"101", "202"}, new float[]{1, 1}, lines, null, size));
        return file;
    }

    @Test
    void itCountsThePagesAndDrawsOneAtTheScaleAskedFor() throws Exception {
        try (PdfPageRenderer pages = PdfPageRenderer.open(report("long.pdf", PageSize.A4, 120))) {
            assertTrue(pages.pageCount() > 1, "the report runs over pages: " + pages.pageCount());
            BufferedImage last = pages.render(pages.pageCount() - 1, 0.5f);
            assertEquals(Math.round(PageSize.A4.getWidth() * 0.5f), last.getWidth(), 1);
            assertEquals(Math.round(PageSize.A4.getHeight() * 0.5f), last.getHeight(), 1);
        }
    }

    /** A report with many columns is printed sideways, and the preview has to know it is wide. */
    @Test
    void aSidewaysPageIsWide() throws Exception {
        try (PdfPageRenderer pages = PdfPageRenderer.open(report("wide.pdf", PageSize.A4.rotate(), 1))) {
            assertEquals(PageSize.A4.getHeight(), pages.pageWidth(0), 0.5);
            assertEquals(PageSize.A4.getWidth(), pages.pageHeight(0), 0.5);
        }
    }

    @Test
    void aScaleBeyondTheLimitIsDrawnAtTheLimit() throws Exception {
        try (PdfPageRenderer pages = PdfPageRenderer.open(report("a5.pdf", PageSize.A5, 1))) {
            BufferedImage page = pages.render(0, 50f);
            assertEquals(Math.round(PageSize.A5.getWidth() * PdfPageRenderer.MAX_SCALE), page.getWidth(), 1);
        }
    }
}
