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

    /**
     * A tree report: a branch heading above its own rows and its summary, the next branch below
     * that, and every line of cells running right to left like the headings.
     */
    @Test
    void aTreeReportPrintsEachBranchHeadingAboveItsRowsAndItsSummary() throws Exception {
        String pdf = dir.resolve("tree.pdf").toString();
        TreePdfLayout layout = new TreePdfLayout(
                new String[]{"101", "202", "303"}, new float[]{1, 1, 1},
                List.of(new TreePdfLayout.Branch("7001", List.<String[]>of(new String[]{"111", "222", "333"}),
                                new String[]{"511", "522", "533"}),
                        new TreePdfLayout.Branch("7002", List.<String[]>of(new String[]{"444", "555", "666"}),
                                new String[]{"611", "622", "633"})),
                new String[]{"911", "922", "933"});
        assertTrue(new PdfExportService().exportTreeReport(pdf, "1", "", layout, PageSize.A4));

        Set<String> wanted = Set.of("101", "202", "303", "7001", "111", "222", "333", "511", "522", "533",
                "7002", "444", "555", "666", "611", "622", "633", "911", "922", "933");
        Map<String, Float> x = textPositions(pdf, wanted);
        Map<String, Float> y = textPositions(pdf, wanted, 1);

        assertOrderedRightToLeft(x, "111", "222", "333");
        assertOrderedRightToLeft(x, "511", "522", "533");
        assertOrderedRightToLeft(x, "444", "555", "666");
        assertOrderedRightToLeft(x, "911", "922", "933");

        // PDF y grows upwards: each line printed below the previous one has a smaller y.
        String[] topToBottom = {"101", "7001", "111", "511", "7002", "444", "611", "911"};
        for (int i = 1; i < topToBottom.length; i++) {
            assertNotNull(y.get(topToBottom[i]), topToBottom[i] + " was not found on the page: " + y);
            assertTrue(y.get(topToBottom[i - 1]) > y.get(topToBottom[i]),
                    topToBottom[i - 1] + " should print above " + topToBottom[i] + ": " + y);
        }
    }

    /** The x of each wanted string's baseline start on page 1. */
    private static Map<String, Float> textPositions(String pdf, Set<String> wanted) throws Exception {
        return textPositions(pdf, wanted, 0);
    }

    /** One coordinate (0 = x, 1 = y) of each wanted string's baseline start on page 1. */
    private static Map<String, Float> textPositions(String pdf, Set<String> wanted, int axis) throws Exception {
        Map<String, Float> found = new HashMap<>();
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            new PdfCanvasProcessor(new IEventListener() {
                @Override
                public void eventOccurred(IEventData data, EventType type) {
                    if (type == EventType.RENDER_TEXT) {
                        TextRenderInfo info = (TextRenderInfo) data;
                        String text = info.getText();
                        if (text != null && wanted.contains(text.strip())) {
                            found.put(text.strip(), info.getBaseline().getStartPoint().get(axis));
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
