package com.hamza.account.features.export;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * A subtitle of two lines prints them in the order they were written. It is shaped into display
     * order before iText wraps it, so a single wrapped Arabic paragraph put its end on the first line -
     * a treasury statement's subtitle printed its closing note above its dates, the start date split at
     * its hyphen. Each line is a paragraph of its own.
     */
    @Test
    void aSubtitleOfTwoLinesPrintsThemInOrder() throws Exception {
        String pdf = dir.resolve("subtitle.pdf").toString();
        assertTrue(new PdfExportService().exportGroupedReport(pdf, "1", "7001\n7002",
                new String[]{"101"}, new float[]{1}, List.<String[]>of(new String[]{"111"}), null, PageSize.A4));

        Map<String, Float> y = textPositions(pdf, Set.of("7001", "7002", "101"), 1);

        for (String text : new String[]{"7001", "7002", "101"}) {
            assertNotNull(y.get(text), text + " was not found on the page: " + y);
        }
        assertTrue(y.get("7001") > y.get("7002") && y.get("7002") > y.get("101"),
                "the first line above the second, both above the table: " + y);
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

    /**
     * A statement above its table: a section's heading, its lines and subtotal, a result on the band,
     * each line of cells right to left - then the table of rows below all of it.
     */
    @Test
    void aStatementPrintsItsLinesInOrderAndItsTableUnderneath() throws Exception {
        String pdf = dir.resolve("statement.pdf").toString();
        StatementPdfLayout statement = new StatementPdfLayout(new String[]{"101", "202", "303"},
                new float[]{2, 1, 1}, List.of(
                new StatementPdfLayout.Line(StatementPdfLayout.Style.HEADING, new String[]{"7001"}),
                new StatementPdfLayout.Line(StatementPdfLayout.Style.ROW, new String[]{"111", "222", "333"}),
                new StatementPdfLayout.Line(StatementPdfLayout.Style.SUBTOTAL, new String[]{"511", "522", "533"}),
                new StatementPdfLayout.Line(StatementPdfLayout.Style.RESULT, new String[]{"611", "622", "633"})));
        assertTrue(new PdfExportService().exportStatementReport(pdf, "1", "", statement,
                new String[]{"801", "802"}, new float[]{1, 1}, List.<String[]>of(new String[]{"811", "812"}),
                new String[]{"911", "912"}, PageSize.A4));

        Set<String> wanted = Set.of("101", "202", "303", "7001", "111", "222", "333", "511", "522", "533",
                "611", "622", "633", "801", "802", "811", "812", "911", "912");
        Map<String, Float> x = textPositions(pdf, wanted);
        Map<String, Float> y = textPositions(pdf, wanted, 1);

        assertOrderedRightToLeft(x, "101", "202", "303");
        assertOrderedRightToLeft(x, "111", "222", "333");
        assertOrderedRightToLeft(x, "511", "522", "533");
        assertOrderedRightToLeft(x, "611", "622", "633");
        assertTrue(x.get("811") > x.get("812") && x.get("911") > x.get("912"), "the table too: " + x);

        String[] topToBottom = {"101", "7001", "111", "511", "611", "801", "811", "911"};
        for (int i = 1; i < topToBottom.length; i++) {
            assertNotNull(y.get(topToBottom[i]), topToBottom[i] + " was not found on the page: " + y);
            assertTrue(y.get(topToBottom[i - 1]) > y.get(topToBottom[i]),
                    topToBottom[i - 1] + " should print above " + topToBottom[i] + ": " + y);
        }
    }

    @Test
    void aStatementLineOfTheWrongWidthIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new StatementPdfLayout(
                new String[]{"1", "2"}, new float[]{1, 1},
                List.of(new StatementPdfLayout.Line(StatementPdfLayout.Style.ROW, new String[]{"1"}))));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new StatementPdfLayout(
                new String[]{"1", "2"}, new float[]{1, 1},
                List.of(new StatementPdfLayout.Line(StatementPdfLayout.Style.HEADING, new String[]{"1", "2"}))));
    }

    /**
     * An invoice: an upright page whatever it holds - the report path turned an eight-column
     * table sideways - with its lines right to left, and the summary box on the left half under
     * them.
     */
    @Test
    void aDocumentStaysUprightAndPutsItsSummaryOnTheLeftUnderItsLines() throws Exception {
        String pdf = dir.resolve("document.pdf").toString();
        String[] headers = {"101", "202", "303", "404", "505", "606", "707", "808"};
        DocumentPdfPage page = new DocumentPdfPage("1", "2", List.of("3"), null,
                List.of(DocumentPdfPage.Field.of("4", "5")),
                List.of(DocumentPdfPage.Field.of("6", "7"), DocumentPdfPage.Field.of("8", "9")),
                headers, new float[]{1, 3, 2, 1, 1, 1, 1, 1},
                List.<String[]>of(new String[]{"11", "12", "13", "14", "15", "16", "17", "18"}),
                new String[]{"", "921", "", "", "", "", "927", "928"},
                List.of(DocumentPdfPage.Field.of("931", "932"), DocumentPdfPage.Field.emphasised("941", "942")),
                "", "", "", "");
        assertTrue(new PdfExportService().exportDocument(pdf, page, PageSize.A4));

        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            var size = document.getPage(1).getPageSize();
            assertTrue(size.getHeight() > size.getWidth(), "an invoice page is upright: " + size);
        }

        Set<String> wanted = Set.of("101", "202", "808", "11", "12", "18", "921", "928", "932", "942");
        Map<String, Float> x = textPositions(pdf, wanted);
        Map<String, Float> y = textPositions(pdf, wanted, 1);

        assertOrderedRightToLeft(x, "101", "202", "808");
        assertOrderedRightToLeft(x, "11", "12", "18");
        assertTrue(x.get("921") > x.get("928"), "the totals line runs right to left: " + x);
        float middle = PageSize.A4.getWidth() / 2;
        assertTrue(x.get("932") < middle && x.get("942") < middle, "the summary sits on the left half: " + x);
        assertTrue(y.get("18") > y.get("928") && y.get("928") > y.get("932") && y.get("932") > y.get("942"),
                "the lines, then their totals, then the summary top to bottom: " + y);
    }

    /**
     * The shop's {@link ReportStyle}, read back out of the file it printed: the numbers at the foot, the
     * sizes the text was set in, what the head carries, where a document starts on paper that already
     * has a heading.
     */
    @Nested
    class TheShopsStyle {

        private static final String[] HEADERS = {"101", "202", "303"};
        private static final float[] WIDTHS = {1, 1, 1};

        private static List<String[]> rows(int count) {
            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                rows.add(new String[]{"111", "222", "333"});
            }
            return rows;
        }

        private static ReportSetup setup(ReportStyle style) {
            return new ReportSetup(style, new ReportLetterhead("5501", List.of("5502"), null),
                    new ReportLabels("", "", "%d : %d", ""), "");
        }

        private String report(String name, ReportStyle style, int rows) {
            String pdf = dir.resolve(name + ".pdf").toString();
            assertTrue(new PdfExportService(setup(style)).exportGroupedReport(pdf, "7777", "8888",
                    HEADERS, WIDTHS, rows(rows), new String[]{"911", "922", "933"}, PageSize.A4));
            return pdf;
        }

        /** A report carried no page number at all; by default it now numbers them as an invoice does. */
        @Test
        void everyPageOfAReportIsNumberedByDefault() throws Exception {
            String pdf = report("numbered", ReportStyle.DEFAULT, 80);
            int pages = pageCount(pdf);
            assertTrue(pages > 1, "the report should run over several pages: " + pages);
            for (int page = 1; page <= pages; page++) {
                assertTrue(pageText(pdf, page).contains(page + " / " + pages),
                        "page " + page + " should say its number: " + pageText(pdf, page));
            }
        }

        @Test
        void thePageNumberFollowsTheChosenPatternOrIsLeftOff() throws Exception {
            String worded = report("worded", ReportStyle.DEFAULT.toBuilder()
                    .pageNumbering(PageNumbering.PAGE_OF_TOTAL).build(), 80);
            assertTrue(pageText(worded, 2).contains("2 : " + pageCount(worded)), pageText(worded, 2));

            String bare = report("bare", ReportStyle.DEFAULT.toBuilder().pageNumbering(PageNumbering.NONE).build(), 80);
            assertFalse(pageText(bare, 1).contains(" / "), pageText(bare, 1));
        }

        /** The sizes the shop chose are the sizes the text was set in. */
        @Test
        void theHeadingsTheRowsAndTheTotalsAreSetInTheirOwnSizes() throws Exception {
            String pdf = report("sizes", ReportStyle.DEFAULT.toBuilder()
                    .titleSize(26).headerSize(16).bodySize(8).totalsSize(13).build(), 1);
            Map<String, Float> size = fontSizes(pdf, Set.of("7777", "101", "111", "911"));
            assertEquals(26f, size.get("7777"), 0.01f);
            assertEquals(16f, size.get("101"), 0.01f);
            assertEquals(8f, size.get("111"), 0.01f);
            assertEquals(13f, size.get("911"), 0.01f);
        }

        @Test
        void theCompanyHeadsAReportOnlyWhenAskedAndAboveItsTitle() throws Exception {
            Map<String, Float> without = textPositions(report("plain", ReportStyle.DEFAULT, 1),
                    Set.of("5501", "7777"), 1);
            assertNull(without.get("5501"), "no report carried the company before it was asked for");

            Map<String, Float> with = textPositions(report("letterhead",
                    ReportStyle.DEFAULT.toBuilder().showLetterhead(true).build(), 1), Set.of("5501", "5502", "7777"), 1);
            assertNotNull(with.get("5501"), "the company's name: " + with);
            assertNotNull(with.get("5502"), "the line under it: " + with);
            assertTrue(with.get("5501") > with.get("5502") && with.get("5502") > with.get("7777"),
                    "the company, then its line, then the title, top to bottom: " + with);
        }

        @Test
        void theTitleAndTheSubtitleCanBeLeftOff() throws Exception {
            Map<String, Float> found = textPositions(report("headless", ReportStyle.DEFAULT.toBuilder()
                    .showTitle(false).showSubtitle(false).build(), 1), Set.of("7777", "8888", "101"), 1);
            assertNull(found.get("7777"), found.toString());
            assertNull(found.get("8888"), found.toString());
            assertNotNull(found.get("101"), "the table is still there: " + found);
        }

        /** The Naskh face's tall line is what made a row nearly three times its text; compact rows are not. */
        @Test
        void compactRowsFitMoreOfThemOnAPage() throws Exception {
            int ordinary = pageCount(report("ordinary", ReportStyle.DEFAULT, 120));
            int compact = pageCount(report("compact", ReportStyle.DEFAULT.toBuilder().compactRows(true).build(), 120));
            assertTrue(compact < ordinary, "compact " + compact + " pages, ordinary " + ordinary);
        }

        private String document(String name, ReportStyle style) throws Exception {
            String pdf = dir.resolve(name + ".pdf").toString();
            DocumentPdfPage page = new DocumentPdfPage("4401", "5501", List.of("5502"), null,
                    List.of(DocumentPdfPage.Field.of("4", "5")), List.of(),
                    new String[]{"101", "202"}, new float[]{1, 1},
                    List.<String[]>of(new String[]{"11", "12"}), null,
                    List.of(DocumentPdfPage.Field.emphasised("941", "942")), "", "", "", "");
            assertTrue(new PdfExportService(setup(style)).exportDocument(pdf, page, PageSize.A4));
            return pdf;
        }

        /** Paper with the company already printed at its head: no letterhead, and the page starts lower. */
        @Test
        void aDocumentCanLeaveItsLetterheadToPrintedPaper() throws Exception {
            Map<String, Float> printed = textPositions(document("with", ReportStyle.DEFAULT), Set.of("5501", "4401"), 1);
            assertNotNull(printed.get("5501"), "every invoice carries its letterhead by default: " + printed);

            ReportStyle preprinted = ReportStyle.DEFAULT.toBuilder()
                    .showDocumentLetterhead(false).documentTopSpaceMm(30).build();
            Map<String, Float> left = textPositions(document("without", preprinted), Set.of("5501", "4401"), 1);
            assertNull(left.get("5501"), "the paper already says who the company is: " + left);
            assertNotNull(left.get("4401"), "the document's own name stays: " + left);
            float drop = printed.get("4401") - left.get("4401");
            assertTrue(drop > 30 * 72 / 25.4f - 20,
                    "thirty millimetres left for the printed heading, moved " + drop + " points");
        }

        @Test
        void aDocumentsPageNumberFollowsTheStyleToo() throws Exception {
            assertTrue(pageText(document("slash", ReportStyle.DEFAULT), 1).contains("1 / 1"));
            assertFalse(pageText(document("none", ReportStyle.DEFAULT.toBuilder()
                    .pageNumbering(PageNumbering.NONE).build()), 1).contains("1 / 1"));
        }
    }

    /**
     * Arabic that wraps in a cell prints its first words on its first line. It was shaped - put in the
     * order it is drawn - before iText wrapped it, so an item's name too long for its column printed its
     * end first, in every report and every invoice. The numbers between the words say which line holds
     * which part.
     */
    @Nested
    class TextThatWraps {

        private static final String LONG = "بند 11 بند 22 بند 33 بند 44 بند 55 بند 66 بند 77 بند 88 بند 99";

        @Test
        void aReportCellPrintsItsBeginningOnItsFirstLine() throws Exception {
            String pdf = dir.resolve("wrapped-report.pdf").toString();
            assertTrue(new PdfExportService(ReportSetup.plain()).exportGroupedReport(pdf, "1", "",
                    new String[]{"101", "202"}, new float[]{1, 6},
                    List.<String[]>of(new String[]{LONG, "111"}), null, PageSize.A4));
            assertBeginsAboveItsEnd(pageText(pdf, 1));
        }

        @Test
        void aDocumentLinePrintsItsBeginningOnItsFirstLine() throws Exception {
            String pdf = dir.resolve("wrapped-document.pdf").toString();
            DocumentPdfPage page = new DocumentPdfPage("1", "2", List.of(), null, List.of(), List.of(),
                    new String[]{"101", "202", "303"}, new float[]{1, 1, 6},
                    List.<String[]>of(new String[]{"11", LONG, "33"}), null, List.of(), "", "", "", "");
            assertTrue(new PdfExportService(ReportSetup.plain()).exportDocument(pdf, page, PageSize.A4));
            assertBeginsAboveItsEnd(pageText(pdf, 1));
        }

        /**
         * A word no line can break - a heading in English, a barcode - widens its column, and the room
         * comes out of the others. The lines were measured against the widths the report asked for, so
         * a cell in a column that had given its room away was kept on lines too long for it, and iText
         * wrapped each of them again after it was shaped - printing its end first. Found on an English
         * item report: "Shortfall" widened its column, and "أقل من الحد" printed "من" / "أقل" / "الحد".
         */
        @Test
        void aColumnThatGaveItsRoomToAnUnbreakableWordStillWrapsInOrder() throws Exception {
            // Numbers no date or time on the page can hold: the line under the title prints the clock.
            String numbered = "بند 701 بند 702 بند 703 بند 704 بند 705 بند 706";
            ReportSetup english = new ReportSetup(ReportStyle.DEFAULT, ReportLetterhead.EMPTY, ReportLabels.NONE,
                    "", false);
            for (ReportSetup setup : List.of(ReportSetup.plain(), english)) {
                String pdf = dir.resolve("narrowed-" + setup.rightToLeft() + ".pdf").toString();
                assertTrue(new PdfExportService(setup).exportGroupedReport(pdf, "1", "",
                        new String[]{"W".repeat(30), "202", "303"}, new float[]{1, 1, 1},
                        List.<String[]>of(new String[]{"", numbered, ""}), null, PageSize.A4));
                assertLinesInOrder(pageText(pdf, 1), "701", "702", "703", "704", "705", "706");
            }
        }

        /**
         * No column is narrower than its widest word; one held there takes that much, the others share
         * what is left by their weights, and one that falls below its own narrowest in turn is held too.
         */
        @Test
        void aColumnHeldAtItsNarrowestLeavesTheRestToTheOthersByWeight() {
            assertArrayEquals(new float[]{100, 100, 100},
                    PdfExportService.fitted(new float[]{1, 1, 1}, new float[]{0, 0, 0}, 300), 0.01f);
            assertArrayEquals(new float[]{60, 120, 120},
                    PdfExportService.fitted(new float[]{1, 1, 2}, new float[]{0, 120, 0}, 300), 0.01f);
            assertArrayEquals(new float[]{140, 70, 90},
                    PdfExportService.fitted(new float[]{1, 1, 1}, new float[]{140, 0, 90}, 300), 0.01f,
                    "held at 140, the third falls to 80 and is held at its 90");
            assertArrayEquals(new float[]{150, 150, 0},
                    PdfExportService.fitted(new float[]{1, 1, 1}, new float[]{200, 200, 0}, 300), 0.01f,
                    "narrowest widths that cannot fit share the page by what they need");
        }

        /** Each token on the same line as the one before it or below it. */
        private void assertLinesInOrder(String text, String... tokens) {
            String[] lines = text.split("\n");
            int previous = -1;
            for (String token : tokens) {
                int line = -1;
                for (int i = 0; i < lines.length && line < 0; i++) {
                    if (lines[i].contains(token)) {
                        line = i;
                    }
                }
                assertTrue(line >= 0, token + " should be on the page: " + text);
                assertTrue(line >= previous, token + " should not print above the text before it: " + text);
                previous = line;
            }
        }

        private void assertBeginsAboveItsEnd(String text) {
            String[] lines = text.split("\n");
            int first = -1;
            int last = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("22") && first < 0) {
                    first = i;
                }
                if (lines[i].contains("99")) {
                    last = i;
                }
            }
            assertTrue(first >= 0 && last >= 0, "both ends should be on the page: " + text);
            assertTrue(first < last, "the text should wrap, its beginning above its end: " + text);
        }

        /** Broken at spaces, as many words to a line as fit, a break already in the text kept. */
        @Test
        void theLinesAreAsFullAsTheWidthAllows() throws Exception {
            var font = com.itextpdf.kernel.font.PdfFontFactory.createFont();
            float word = font.getWidth("aaa", 10);
            float space = font.getWidth(" ", 10);
            float twoWords = 2 * word + space;

            assertEquals(List.of("aaa aaa", "aaa"), PdfExportService.lines("aaa aaa aaa", font, 10, twoWords + 0.5f));
            assertEquals(List.of("aaa", "aaa aaa"), PdfExportService.lines("aaa\naaa aaa", font, 10, twoWords + 0.5f));
            assertEquals(List.of("aaa aaa aaa"), PdfExportService.lines("aaa aaa aaa", font, 10, 0),
                    "no width is no breaking");
            assertEquals(List.of("aaaaaaaaaaaa"), PdfExportService.lines("aaaaaaaaaaaa", font, 10, word),
                    "a word wider than the line is left whole for iText to split");
        }
    }

    /**
     * A report runs the way its reader reads. In English it ran right to left like an Arabic one - its
     * first column on the right under an English heading; the audit log's own renderer was the one PDF
     * that did not, and it prints through this service now.
     */
    @Nested
    class LeftToRight {

        private final ReportSetup english = new ReportSetup(ReportStyle.DEFAULT, ReportLetterhead.EMPTY,
                ReportLabels.NONE, "", false);

        @Test
        void aReportsHeadingsRowsAndTotalsRunLeftToRight() throws Exception {
            String pdf = dir.resolve("ltr-grouped.pdf").toString();
            assertTrue(new PdfExportService(english).exportGroupedReport(pdf, "1", "",
                    new String[]{"101", "202", "303"}, new float[]{1, 1, 1},
                    List.<String[]>of(new String[]{"111", "222", "333"}),
                    new String[]{"911", "922", "933"}, PageSize.A4));

            Map<String, Float> x = textPositions(pdf, Set.of("101", "202", "303",
                    "111", "222", "333", "911", "922", "933"));
            assertOrderedRightToLeft(x, "303", "202", "101");
            assertOrderedRightToLeft(x, "333", "222", "111");
            assertOrderedRightToLeft(x, "933", "922", "911");
        }

        /** The one-figure totals line: the caption across the left, the figure under the last column. */
        @Test
        void aSingleTotalSitsUnderTheLastColumn() throws Exception {
            String pdf = dir.resolve("ltr-generic.pdf").toString();
            assertTrue(new PdfExportService(english).exportGenericReport(pdf, "1", "",
                    new String[]{"101", "202", "303"}, new float[]{1, 1, 1},
                    List.<String[]>of(new String[]{"111", "222", "333"}), "901", "902", null, PageSize.A4));

            Map<String, Float> x = textPositions(pdf, Set.of("303", "333", "901", "902"));
            assertTrue(x.get("901") < x.get("902"), "the caption left of the figure: " + x);
            // Both are centred in their cell, so they start within a few points of each other.
            assertEquals(x.get("303"), x.get("902"), 10f, "the figure under the last column's heading: " + x);
        }

        @Test
        void aTreesLinesRunLeftToRight() throws Exception {
            String pdf = dir.resolve("ltr-tree.pdf").toString();
            TreePdfLayout layout = new TreePdfLayout(new String[]{"101", "202"}, new float[]{1, 1},
                    List.of(new TreePdfLayout.Branch("7001", List.<String[]>of(new String[]{"111", "222"}),
                            new String[]{"511", "522"})), new String[]{"911", "922"});
            assertTrue(new PdfExportService(english).exportTreeReport(pdf, "1", "", layout, PageSize.A4));

            Map<String, Float> x = textPositions(pdf, Set.of("111", "222", "511", "522", "911", "922"));
            assertTrue(x.get("111") < x.get("222") && x.get("511") < x.get("522") && x.get("911") < x.get("922"),
                    "every line left to right: " + x);
        }

        /** An invoice keeps its right-to-left layout whatever the reader's language: it is drawn for it. */
        @Test
        void aDocumentStillRunsRightToLeft() throws Exception {
            String pdf = dir.resolve("ltr-document.pdf").toString();
            DocumentPdfPage page = new DocumentPdfPage("1", "2", List.of(), null, List.of(), List.of(),
                    new String[]{"101", "202", "303"}, new float[]{1, 1, 1},
                    List.<String[]>of(new String[]{"11", "12", "13"}), null, List.of(), "", "", "", "");
            assertTrue(new PdfExportService(english).exportDocument(pdf, page, PageSize.A4));

            assertOrderedRightToLeft(textPositions(pdf, Set.of("101", "202", "303")), "101", "202", "303");
        }
    }

    private static int pageCount(String pdf) throws Exception {
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            return document.getNumberOfPages();
        }
    }

    private static String pageText(String pdf, int page) throws Exception {
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            return PdfTextExtractor.getTextFromPage(document.getPage(page));
        }
    }

    /** The size each wanted string was set in on page 1, as the page's own text state says. */
    private static Map<String, Float> fontSizes(String pdf, Set<String> wanted) throws Exception {
        Map<String, Float> found = new HashMap<>();
        try (PdfDocument document = new PdfDocument(new PdfReader(pdf))) {
            new PdfCanvasProcessor(new IEventListener() {
                @Override
                public void eventOccurred(IEventData data, EventType type) {
                    if (type == EventType.RENDER_TEXT) {
                        TextRenderInfo info = (TextRenderInfo) data;
                        String text = info.getText();
                        if (text != null && wanted.contains(text.strip())) {
                            found.put(text.strip(), info.getFontSize());
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
