package com.hamza.account.features.export;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a grouped report puts its cells, read back out of a real PDF.
 * <p>
 * <b>The totals line was mirrored against its own columns.</b> The headers and the rows are
 * reversed on their way into the table, so the first logical column lands on the right; the
 * totals line was not, so its label printed under the last column and every figure under the
 * wrong heading - in the totals screen's reports, the accounts screen's and the ageing report's.
 * The text extracted from such a PDF is correct, which is why only positions can catch it.
 * <p>
 * Digits only: the bundled Arabic font draws digits, and a Latin letter may not be in it.
 */
class PdfExportServiceLayoutTest {

    @TempDir
    Path dir;

    @Test
    void theTotalsLineRunsRightToLeftLikeTheRowsAboveIt() throws Exception {
        String pdf = dir.resolve("grouped.pdf").toString();
        boolean written = new PdfExportService().exportGroupedReport(pdf, "1", "",
                new String[]{"101", "202", "303"}, new float[]{1, 1, 1},
                List.<String[]>of(new String[]{"111", "222", "333"}),
                new String[]{"911", "922", "933"}, PageSize.A4);
        assertTrue(written);

        Map<String, Float> x = textPositions(pdf, Set.of("101", "202", "303",
                "111", "222", "333", "911", "922", "933"));

        // The first logical column is the rightmost, for the headings, the rows and the totals.
        assertOrderedRightToLeft(x, "101", "202", "303");
        assertOrderedRightToLeft(x, "111", "222", "333");
        assertOrderedRightToLeft(x, "911", "922", "933");
    }

    private static void assertOrderedRightToLeft(Map<String, Float> x, String first, String second,
                                                 String third) {
        for (String text : new String[]{first, second, third}) {
            assertNotNull(x.get(text), text + " was not found on the page: " + x);
        }
        assertTrue(x.get(first) > x.get(second) && x.get(second) > x.get(third),
                first + ", " + second + ", " + third + " should run right to left: " + x);
    }

    /** The x of each wanted string's baseline start on page 1. */
    private static Map<String, Float> textPositions(String pdf, Set<String> wanted) throws Exception {
        Map<String, Float> found = new HashMap<>();
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            new PdfCanvasProcessor(new IEventListener() {
                @Override
                public void eventOccurred(IEventData data, EventType type) {
                    if (type == EventType.RENDER_TEXT) {
                        TextRenderInfo info = (TextRenderInfo) data;
                        String text = info.getText();
                        if (text != null && wanted.contains(text.strip())) {
                            found.put(text.strip(), info.getBaseline().getStartPoint().get(0));
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
