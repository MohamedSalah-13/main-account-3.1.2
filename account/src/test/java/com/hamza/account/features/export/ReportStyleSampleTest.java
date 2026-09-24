package com.hamza.account.features.export;

import com.itextpdf.kernel.geom.PageSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The made-up pages the settings screen previews a style on. Wrong here is quiet - a preview that
 * cannot be drawn says only "could not prepare the preview" - so the shape is held here instead.
 */
class ReportStyleSampleTest {

    /** Every key answers itself, with its placeholders, as a missing translation would. */
    private static final ReportStyleSample.Labels KEYS = key -> switch (key) {
        case "report.style.sample.item" -> "item %d";
        case "report.style.sample.subtitle" -> "%s .. %s";
        default -> key;
    };

    @TempDir
    Path dir;

    @Test
    void theReportIsLongEnoughToReachItsFootAndItsTotalsAddUp() {
        ReportStyleSample.SampleReport report = ReportStyleSample.report(KEYS, LocalDate.of(2026, 9, 23));

        assertEquals(ReportStyleSample.REPORT_ROWS, report.rows().size());
        assertEquals("2026-09-01 .. 2026-09-23", report.subtitle());
        assertEquals("item 7", report.rows().get(6)[1]);
        BigDecimal sum = BigDecimal.ZERO;
        int quantity = 0;
        for (String[] row : report.rows()) {
            assertEquals(report.headers().length, row.length);
            sum = sum.add(new BigDecimal(row[4].replace(",", "")));
            quantity += Integer.parseInt(row[2]);
        }
        assertEquals(String.valueOf(quantity), report.totals()[2]);
        assertEquals(0, sum.compareTo(new BigDecimal(report.totals()[4].replace(",", ""))));
    }

    /** A translator's broken placeholder previews as its own words rather than failing the preview. */
    @Test
    void aBrokenPlaceholderDoesNotFailTheSample() {
        ReportStyleSample.Labels broken = key -> key.equals("report.style.sample.subtitle") ? "%d" : "x %d %d";
        ReportStyleSample.SampleReport report = ReportStyleSample.report(broken, LocalDate.of(2026, 9, 23));
        assertEquals("%d", report.subtitle());
        assertEquals("x %d %d", report.rows().getFirst()[1]);
    }

    @Test
    void theInvoiceCarriesTheShopsLetterhead() {
        ReportLetterhead company = new ReportLetterhead("شركة", List.of("القاهرة"), null);
        DocumentPdfPage page = ReportStyleSample.document(KEYS, company, LocalDate.of(2026, 9, 23), "10:30");

        assertEquals("شركة", page.companyName());
        assertEquals(List.of("القاهرة"), page.companyLines());
        assertEquals(ReportStyleSample.DOCUMENT_LINES, page.rows().size());
        assertTrue(page.footer().endsWith("10:30"));
    }

    /** Both pages go through the real renderer in every style the screen offers a choice between. */
    @Test
    void bothPagesArePrintedInEveryPaletteAndWithInkSaved() {
        for (ReportPalette palette : ReportPalette.values()) {
            for (boolean inkSaver : new boolean[]{false, true}) {
                ReportStyle style = ReportStyle.DEFAULT.toBuilder().palette(palette).inkSaver(inkSaver)
                        .showLetterhead(true).build();
                ReportSetup setup = new ReportSetup(style, new ReportLetterhead("1", List.of("2"), null),
                        ReportLabels.NONE, "");
                ReportStyleSample.SampleReport report = ReportStyleSample.report(KEYS, LocalDate.of(2026, 9, 23));
                String name = palette + "-" + inkSaver;
                assertTrue(new PdfExportService(setup).exportGroupedReport(dir.resolve(name + ".pdf").toString(),
                        report.title(), report.subtitle(), report.headers(), report.columnWidths(), report.rows(),
                        report.totals(), PageSize.A4));
                assertTrue(new PdfExportService(setup).exportDocument(dir.resolve(name + "-doc.pdf").toString(),
                        ReportStyleSample.document(KEYS, setup.letterhead(), LocalDate.of(2026, 9, 23), ""),
                        PageSize.A5));
            }
        }
    }
}
