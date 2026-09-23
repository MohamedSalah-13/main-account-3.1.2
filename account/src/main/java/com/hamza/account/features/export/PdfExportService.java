package com.hamza.account.features.export;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.*;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * خدمة تصدير البيانات إلى ملفات PDF
 * تدعم اللغة العربية والتنسيق الاحترافي
 * <p>
 * <b>How a page looks is the {@link ReportSetup}'s, not this class's.</b> The sizes, the colours, what
 * the head and the foot carry and whether the pages are numbered were constants here; they are the
 * shop's {@link ReportStyle} now, and the no-argument constructor reads the one installed at start-up
 * ({@link ReportSetups}), so every screen that prints prints in it without being told. The default
 * style is the constants' values, so a page printed with it is the page printed before.
 *
 * @author Hamza
 * @version 1.2
 */
@Log4j2
public class PdfExportService {

    // الخط يجب أن يكون داخل resources لنتمكن من قراءته من داخل JAR
    private static final String ARABIC_FONT_RESOURCE =
            "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final String ARABIC_FONT_BOLD_RESOURCE =
            "/com/hamza/account/fonts/NotoNaskhArabic-Bold.ttf";

    private static final DeviceRgb ALTERNATE_ROW_COLOR = new DeviceRgb(236, 240, 241);
    private static final DateTimeFormatter PRINTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** A report's margins; the foot is taller when a page number sits in it. */
    private static final float REPORT_MARGIN = 20;
    private static final float NUMBERED_FOOT_MARGIN = 34;
    /** Where a page number's baseline sits, measured up from the page's bottom edge. */
    private static final float PAGE_NUMBER_Y = 16;
    /** The line height of a compact row, as a multiple of its type size - the one a document uses. */
    private static final float COMPACT_LEADING = 1.45f;
    /** Taken off a cell's width beyond its padding: its two borders and a point to spare. */
    private static final float CELL_SLACK = 2;
    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final ReportSetup setup;
    private final ReportStyle style;
    private final DeviceRgb headingColor;
    private final DeviceRgb totalsColor;
    private final DeviceRgb bandColor;
    private final DeviceRgb bandTextColor;

    private PdfFont arabicFont;
    private PdfFont boldFont;

    /** The width a page's content spans - the page less its side margins - for the file being written. */
    private float usableWidth = PageSize.A4.getWidth() - 2 * REPORT_MARGIN;
    /** Each table's column widths in points, in the order its cells are added. */
    private final Map<Table, float[]> columnPoints = new IdentityHashMap<>();
    /**
     * Whether the file being written runs right to left: a report as the reader's language does
     * ({@link ReportSetup#rightToLeft}), an invoice or a voucher always, since its layout is drawn for it.
     */
    private boolean rtl = true;

    /** In the shop's style, as installed at start-up - or the default one where nothing is. */
    public PdfExportService() {
        this(ReportSetups.current());
    }

    public PdfExportService(ReportSetup setup) {
        this.setup = Objects.requireNonNull(setup, "setup");
        this.style = setup.style();
        ReportPalette palette = style.palette();
        this.headingColor = rgb(palette.heading());
        this.totalsColor = rgb(palette.totals());
        this.bandColor = rgb(palette.band());
        this.bandTextColor = rgb(palette.bandText());
        initializeFonts();
    }

    private static DeviceRgb rgb(int value) {
        return new DeviceRgb((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
    }

    /**
     * تهيئة الخطوط العربية - يجب تحميل الخط كـ bytes من classpath
     * حتى يعمل داخل JAR أيضاً.
     */
    private void initializeFonts() {
        try {
            byte[] regularBytes = loadFontBytes(ARABIC_FONT_RESOURCE);
            byte[] boldBytes = loadFontBytes(ARABIC_FONT_BOLD_RESOURCE);

            if (regularBytes != null) {
                arabicFont = PdfFontFactory.createFont(
                        regularBytes,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
            } else {
                log.warn("Arabic font not found in resources, using fallback");
                arabicFont = PdfFontFactory.createFont();
            }

            if (boldBytes != null) {
                boldFont = PdfFontFactory.createFont(
                        boldBytes,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
            } else {
                boldFont = arabicFont;
            }
            log.info("Arabic fonts loaded successfully");
        } catch (IOException e) {
            log.error("Error loading Arabic fonts", e);
            try {
                arabicFont = PdfFontFactory.createFont();
                boldFont = arabicFont;
            } catch (IOException ex) {
                log.error("Error loading fallback font", ex);
            }
        }
    }

    private byte[] loadFontBytes(String resourcePath) {
        try (InputStream is = PdfExportService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return null;
            }
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] tmp = new byte[8192];
                int n;
                while ((n = is.read(tmp)) != -1) {
                    buffer.write(tmp, 0, n);
                }
                return buffer.toByteArray();
            }
        } catch (IOException e) {
            log.error("Failed to read font resource: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * إنشاء مستند PDF جديد
     * <p>
     * The pages are kept until the end ({@code immediateFlush} off), because a page's number says how
     * many there are, which is known only once the last one is laid out - {@link #finishReport} writes
     * them. The table is held whole in memory before it is laid out in any case, so keeping its pages
     * adds little to what a long report already costs.
     */
    private Document createDocument(String filePath, PageSize pageSize) throws IOException {
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, pageSize, false);
        rtl = setup.rightToLeft();
        document.setFont(arabicFont);
        document.setFontSize(11);
        // الاتجاه الافتراضي للتقرير: اتجاه لغة القارئ
        document.setProperty(Property.BASE_DIRECTION, direction());
        document.setTextAlignment(start());
        float foot = style.pageNumbering() == PageNumbering.NONE ? REPORT_MARGIN : NUMBERED_FOOT_MARGIN;
        document.setMargins(REPORT_MARGIN, REPORT_MARGIN, foot, REPORT_MARGIN);
        usableWidth = pageSize.getWidth() - 2 * REPORT_MARGIN;
        return document;
    }

    /**
     * فقرة عربية جاهزة مع reshaping و bidi
     */
    private Paragraph arabicParagraph(String text) {
        return new Paragraph(shape(text != null ? text : ""))
                .setFont(arabicFont)
                .setBaseDirection(direction())
                .setTextAlignment(start());
    }

    private Paragraph arabicParagraphBold(String text) {
        String shaped = shape(text != null ? text : "");
        return new Paragraph(shaped)
                .setFont(boldFontFor(shaped))
                .setBold()
                .setBaseDirection(direction())
                .setTextAlignment(start());
    }

    /** A whole text shaped for the page: one with no letter in it takes the page's direction. */
    private String shape(String text) {
        return ArabicTextHelper.shapeLine(text, text, rtl);
    }

    /** The direction the file being written runs in. */
    private BaseDirection direction() {
        return rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT;
    }

    /** The side a line of text starts on: the right in Arabic, the left in English. */
    private TextAlignment start() {
        return rtl ? TextAlignment.RIGHT : TextAlignment.LEFT;
    }

    /**
     * A line's cells in the order they are added to a table. iText places the first cell on the left
     * whatever the direction, so a right-to-left line is reversed on its way in - its first logical
     * column lands on the right - and a left-to-right one goes in as written.
     */
    private String[] inAddedOrder(String[] cells) {
        return rtl ? reverseStrings(cells) : cells;
    }

    private float[] inAddedOrder(float[] widths) {
        return rtl ? reverseFloats(widths) : widths;
    }

    /** Padding on the side a line starts on - where a leaf line is indented under its heading. */
    private Cell paddedAtStart(Cell cell, float padding) {
        return rtl ? cell.setPaddingRight(padding) : cell.setPaddingLeft(padding);
    }

    /**
     * The bold face, unless it cannot draw one of the characters - then the regular face, which
     * the bold is set over anyway.
     * <p>
     * The bundled bold Naskh has no glyph for the hyphen-minus. Every negative figure on a totals
     * line - the one bold row of a table - printed as its number and an empty box: {@code -7,825.00}
     * read as a positive {@code 7,825.00}. Found by rendering a report to an image, which is the
     * only way it could have been: the text extracted from the PDF still says "-".
     */
    private PdfFont boldFontFor(String text) {
        return text.codePoints().allMatch(boldFont::containsGlyph) ? boldFont : arabicFont;
    }

    /** {@link #arabicParagraph(String)} for a place {@code width} points wide, broken into its lines here. */
    private Paragraph arabicParagraph(String text, float size, float width) {
        return wrapped(text, false, size, width);
    }

    /** {@link #arabicParagraphBold(String)} for a place {@code width} points wide, broken into its lines here. */
    private Paragraph arabicParagraphBold(String text, float size, float width) {
        return wrapped(text, true, size, width);
    }

    /**
     * A paragraph broken into the lines that fit {@code width} points at {@code size}, each shaped alone.
     * <p>
     * <b>iText must never be the one to wrap an Arabic line.</b> The text reaches it already shaped - in
     * the order it is drawn - so a line it wrapped put the text's <em>end</em> first: an item's name too
     * long for its column printed "... من إنتاج الشركة" above "زيت عباد الشمس", in every report and every
     * invoice, and a larger type size made it happen more. Here the logical text is broken at its spaces,
     * each candidate line measured in the font it will be drawn in, and each line shaped on its own
     * ({@link ArabicTextHelper#shapeLine}); the lines are joined by breaks iText keeps. A word longer
     * than the whole width is left on a line of its own for iText to split, as before.
     *
     * @param width the room for the text itself, padding and borders already taken off; zero or less
     *              breaks nothing
     */
    private Paragraph wrapped(String text, boolean bold, float size, float width) {
        String logical = text == null ? "" : text;
        String shaped = shape(logical);
        PdfFont font = bold ? boldFontFor(shaped) : arabicFont;
        Paragraph paragraph = new Paragraph()
                .setFont(font)
                .setFontSize(size)
                .setBaseDirection(direction())
                .setTextAlignment(start());
        if (bold) {
            paragraph.setBold();
        }
        // Nearly every cell fits on one line: that one is shaped once, as before, and measured once.
        if (width <= 0 || !logical.contains("\n") && font.getWidth(shaped, size) <= width) {
            return paragraph.add(new Text(shaped));
        }
        List<String> lines = lines(logical, font, size, width);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                paragraph.add(new Text("\n"));
            }
            paragraph.add(new Text(ArabicTextHelper.shapeLine(lines.get(i), logical, rtl)));
        }
        return paragraph;
    }

    /** The logical lines {@code text} breaks into at {@code width}; a line break already in it is kept. */
    static List<String> lines(String text, PdfFont font, float size, float width) {
        List<String> lines = new ArrayList<>();
        for (String hard : text.split("\n", -1)) {
            if (width <= 0 || fits(hard, font, size, width)) {
                lines.add(hard);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : hard.strip().split(" +")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && !fits(candidate, font, size, width)) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static boolean fits(String logical, PdfFont font, float size, float width) {
        return font.getWidth(ArabicTextHelper.shape(logical), size) <= width;
    }

    /** Records a table's columns in points, in the order its cells are added; it spans the usable width. */
    private void registerColumns(Table table, float[] addedOrderWidths) {
        float sum = 0;
        for (float width : addedOrderWidths) {
            sum += width;
        }
        float[] points = new float[addedOrderWidths.length];
        for (int i = 0; i < points.length; i++) {
            points[i] = sum <= 0 ? 0 : usableWidth * addedOrderWidths[i] / sum;
        }
        columnPoints.put(table, points);
    }

    /** The room for text in one column's cell, less the cell's own padding; zero when the table is unknown. */
    private float textWidth(Table table, int addedIndex, float padding) {
        float[] points = columnPoints.get(table);
        if (points == null || addedIndex < 0 || addedIndex >= points.length) {
            return 0;
        }
        return points[addedIndex] - padding - CELL_SLACK;
    }

    /** The room for text in a cell spanning the whole table. */
    private float spanWidth(Table table, float padding) {
        float[] points = columnPoints.get(table);
        if (points == null) {
            return 0;
        }
        float sum = 0;
        for (float point : points) {
            sum += point;
        }
        return sum - padding - CELL_SLACK;
    }

    /**
     * إضافة ترويسة للمستند: the company when the shop asked for it, the title, the subtitle, and the
     * line saying when and by whom it was printed - each only as the style asks.
     */
    private void addHeader(Document document, String title, String subtitle) {
        if (setup.printsLetterhead()) {
            document.add(reportLetterhead(setup.letterhead()));
            document.add(new LineSeparator(rule(1f)).setMarginTop(3).setMarginBottom(6));
        }

        // العنوان الرئيسي
        if (style.showTitle()) {
            Paragraph titlePara = arabicParagraphBold(title, style.titleSize(), usableWidth)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5);
            document.add(titlePara);
        }

        // العنوان الفرعي - سطر لكل '\n'. النص يُشكَّل بترتيب العرض قبل أن يلفّه iText، فالفقرة العربية
        // التي تلتف تضع آخرها في السطر الأول وقد تقسم تاريخًا عند شَرطته؛ السطر المقصود يُكتب فقرةً وحده.
        if (style.showSubtitle() && subtitle != null && !subtitle.isEmpty()) {
            String[] lines = subtitle.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Paragraph subtitlePara = arabicParagraph(lines[i], style.subtitleSize(), usableWidth)
                        .setMarginBottom(i == lines.length - 1 ? 5 : 0);
                document.add(subtitlePara);
            }
        }

        // التاريخ والوقت، ومن طبع التقرير
        String printed = setup.printedLine(LocalDateTime.now().format(PRINTED_AT));
        if (!printed.isEmpty()) {
            document.add(arabicParagraph(printed, style.smallSize(), usableWidth)
                    .setMarginBottom(5));
        }
    }

    /**
     * The company above a report: its name and lines on the right with its picture beside them, the way
     * an invoice's letterhead reads - smaller, since on a report it is not what the page is about.
     */
    private Table reportLetterhead(ReportLetterhead letterhead) {
        Div text = new Div();
        if (!letterhead.name().isEmpty()) {
            text.add(arabicParagraphBold(letterhead.name()).setFontSize(style.subtitleSize() + 2)
                    .setMarginTop(0).setMarginBottom(1));
        }
        for (String line : letterhead.lines()) {
            text.add(arabicParagraph(line).setFontSize(Math.max(ReportStyle.MIN_FONT_SIZE, style.smallSize() - 1))
                    .setFontColor(ColorConstants.DARK_GRAY)
                    .setFixedLeading(style.smallSize() * COMPACT_LEADING)
                    .setMarginTop(0).setMarginBottom(0));
        }
        Image logo = logoImage(letterhead.logo());
        if (logo == null) {
            Table band = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
            band.addCell(new Cell().add(text).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE));
            return band;
        }
        // The picture on the side the page starts on, the name beside it.
        Cell textCell = paddedAtStart(new Cell().add(text).setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE), 6);
        Cell logoCell = new Cell().add(logo.scaleToFit(48, 48)
                        .setHorizontalAlignment(rtl ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT))
                .setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        Table band = new Table(UnitValue.createPercentArray(rtl ? new float[]{84, 16} : new float[]{16, 84}))
                .useAllAvailableWidth();
        band.addCell(rtl ? textCell : logoCell);
        band.addCell(rtl ? logoCell : textCell);
        return band;
    }

    /**
     * The rule under a letterhead, in the heading's colour. The colour belongs to the line drawn, not to
     * the separator holding it: set on the separator it was ignored, and the rule under every invoice's
     * head printed black while the code asked for blue.
     */
    private SolidLine rule(float width) {
        SolidLine line = new SolidLine(width);
        line.setColor(headingColor);
        return line;
    }

    /**
     * A paragraph set in a table cell. A compact row is as tall as its text: no margin above or below,
     * and a fixed line height, because the Naskh face declares a line far taller than its letters.
     */
    private Paragraph inCell(Paragraph paragraph, float size) {
        if (style.compactRows()) {
            paragraph.setMarginTop(0).setMarginBottom(0).setFixedLeading(size * COMPACT_LEADING);
        }
        return paragraph;
    }

    /** A heading row's cell: filled with the heading colour in white, or - saving ink - dark type ruled beneath. */
    private Cell headingCell(Cell cell) {
        if (style.inkSaver()) {
            return cell.setFontColor(bandTextColor).setBorderBottom(new SolidBorder(bandTextColor, 1.2f));
        }
        return cell.setBackgroundColor(headingColor).setFontColor(ColorConstants.WHITE);
    }

    /** A closing totals line's cell: on the totals band in white, or ruled above and below. */
    private Cell totalsCell(Cell cell) {
        if (style.inkSaver()) {
            return cell.setFontColor(bandTextColor)
                    .setBorderTop(new SolidBorder(bandTextColor, 1.2f))
                    .setBorderBottom(new SolidBorder(bandTextColor, 1.2f));
        }
        return cell.setBackgroundColor(totalsColor).setFontColor(ColorConstants.WHITE);
    }

    /** A branch heading, a subtotal or the figure a document's reader looks for: dark type on the light band. */
    private Cell bandCell(Cell cell) {
        cell.setFontColor(bandTextColor);
        return style.inkSaver() ? cell : cell.setBackgroundColor(bandColor);
    }

    /** Every second row striped, unless saving ink. */
    private Cell striped(Cell cell, boolean alternate) {
        return alternate && !style.inkSaver() ? cell.setBackgroundColor(ALTERNATE_ROW_COLOR) : cell;
    }

    /** A branch heading across every column: bold on the band, or ruled beneath when saving ink. */
    private Cell branchHeading(Table table, String title, int columns) {
        Cell cell = bandCell(paddedAtStart(new Cell(1, columns)
                .add(inCell(arabicParagraphBold(title, style.bodySize(), spanWidth(table, 8)), style.bodySize()))
                .setFontSize(style.bodySize())
                .setTextAlignment(start())
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPaddingTop(4)
                .setPaddingBottom(4), 6));
        return style.inkSaver() ? cell.setBorderBottom(new SolidBorder(bandTextColor, 1f)) : cell;
    }

    /**
     * إنشاء جدول مع ترويسة (الجدول يُبنى من اليمين إلى اليسار بعكس ترتيب الأعمدة)
     */
    private Table createTable(String[] headers, float[] columnWidths) {
        // عكس الأعمدة والعناوين لجعل أول عمود منطقي يظهر في أقصى اليمين
        float[] rtlWidths = inAddedOrder(columnWidths);
        String[] rtlHeaders = inAddedOrder(headers);

        Table table = new Table(UnitValue.createPercentArray(rtlWidths));
        table.setBaseDirection(direction());
        table.setTextAlignment(start());
        table.setHorizontalAlignment(rtl ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
        table.setWidth(UnitValue.createPercentValue(100));
        table.setFont(arabicFont);
        registerColumns(table, rtlWidths);

        for (int i = 0; i < rtlHeaders.length; i++) {
            String header = rtlHeaders[i];
            Cell cell = headingCell(new Cell()
                    .add(inCell(arabicParagraphBold(header, style.headerSize(), textWidth(table, i, 4)),
                            style.headerSize()).setTextAlignment(TextAlignment.CENTER))
                    .setFontSize(style.headerSize())
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2));
            table.addHeaderCell(cell);
        }
        return table;
    }

    /**
     * إضافة صف للجدول مع عكس ترتيب الخلايا (RTL)
     */
    private void addTableRow(Table table, String[] rowData, boolean isAlternate) {
        String[] rtlRow = inAddedOrder(rowData);
        for (int i = 0; i < rtlRow.length; i++) {
            Cell cell = new Cell()
                    .add(inCell(arabicParagraph(rtlRow[i], style.bodySize(), textWidth(table, i, 4)), style.bodySize()))
                    .setFontSize(style.bodySize())
                    .setTextAlignment(start())
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2);
            table.addCell(striped(cell, isAlternate));
        }
    }

    /**
     * إضافة صف إجمالي للجدول
     * في RTL: خلية المجموع (الصغيرة) تكون في اليمين، والعنوان يمتد على باقي الأعمدة لليسار.
     */
    /**
     * A grouped report: the same page as {@link #exportGenericReport}, but its last line
     * carries a figure in <b>every</b> column rather than one in the last.
     * <p>
     * A summary by customer whose total line says only the profit leaves the reader adding
     * up the amount column by hand, which is the work the report was supposed to do.
     *
     * @param totals one cell per header, the first being what the line is called
     */
    public boolean exportGroupedReport(String filePath, String title, String subtitle,
                                       String[] headers, float[] columnWidths,
                                       List<String[]> data, String[] totals, PageSize pageSize) {
        return exportChartReport(filePath, title, subtitle, null, headers, columnWidths, data,
                totals, pageSize);
    }

    /**
     * A grouped report with a chart above it: the header, the chart as a picture scaled to the
     * width of the page, then the same table and totals line {@link #exportGroupedReport} writes.
     * <p>
     * The chart arrives as a finished PNG. How it is drawn is the screen's business - this class
     * places a picture, it does not know what a series is.
     *
     * @param chartPng the chart as a PNG, or null for the table alone
     */
    public boolean exportChartReport(String filePath, String title, String subtitle, byte[] chartPng,
                                     String[] headers, float[] columnWidths,
                                     List<String[]> data, String[] totals, PageSize pageSize) {
        try (Document document = createDocument(filePath, pageSize)) {
            addHeader(document, title, subtitle);
            if (chartPng != null && chartPng.length > 0) {
                Image chart = new Image(ImageDataFactory.create(chartPng));
                chart.setAutoScale(true);
                chart.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                chart.setMarginBottom(10);
                document.add(chart);
            }
            Table table = createTable(headers, columnWidths);
            int rowIndex = 0;
            for (String[] row : data) {
                addTableRow(table, row, rowIndex % 2 == 1);
                rowIndex++;
            }
            if (totals != null && totals.length == headers.length) {
                addTotalsRow(table, totals);
            }
            document.add(table);
            finishReport(document);
            log.info("PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    /**
     * A tree report: for each branch a heading line across every column, its rows indented under
     * it, and its summary line; then the closing totals. One table throughout, so the column
     * headings repeat at the top of every page a long branch runs onto.
     */
    public boolean exportTreeReport(String filePath, String title, String subtitle,
                                    TreePdfLayout layout, PageSize pageSize) {
        try (Document document = createDocument(filePath, pageSize)) {
            addHeader(document, title, subtitle);
            Table table = createTable(layout.headers(), layout.columnWidths());
            int columns = layout.headers().length;
            for (TreePdfLayout.Branch branch : layout.branches()) {
                table.addCell(branchHeading(table, branch.title(), columns));
                int rowIndex = 0;
                for (String[] row : branch.rows()) {
                    addBranchRow(table, row, rowIndex % 2 == 1);
                    rowIndex++;
                }
                if (branch.summary() != null) {
                    addBranchSummary(table, branch.summary());
                }
            }
            if (layout.totals() != null) {
                addTotalsRow(table, layout.totals());
            }
            document.add(table);
            finishReport(document);
            log.info("PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    /**
     * A statement above a table of its rows: {@link StatementPdfLayout}'s lines, then, a little lower, the
     * same flat table and totals line {@link #exportGroupedReport} writes. The profit and loss statement
     * prints this way - the page explains the period, the table shows it a row at a time.
     *
     * @param rows   the table's rows, or an empty list for the statement alone
     * @param totals the table's closing line, or null for none
     */
    public boolean exportStatementReport(String filePath, String title, String subtitle,
                                         StatementPdfLayout statement, String[] headers, float[] columnWidths,
                                         List<String[]> rows, String[] totals, PageSize pageSize) {
        try (Document document = createDocument(filePath, pageSize)) {
            addHeader(document, title, subtitle);
            Table page = createTable(statement.headers(), statement.columnWidths());
            int columns = statement.headers().length;
            for (StatementPdfLayout.Line line : statement.lines()) {
                switch (line.style()) {
                    case HEADING -> page.addCell(branchHeading(page, line.cells()[0], columns));
                    case ROW -> addBranchRow(page, line.cells(), false);
                    case SUBTOTAL -> addBranchSummary(page, line.cells());
                    case RESULT -> addTotalsRow(page, line.cells());
                }
            }
            document.add(page);
            if (!rows.isEmpty()) {
                document.add(new Paragraph("\n"));
                Table table = createTable(headers, columnWidths);
                int rowIndex = 0;
                for (String[] row : rows) {
                    addTableRow(table, row, rowIndex % 2 == 1);
                    rowIndex++;
                }
                if (totals != null && totals.length == headers.length) {
                    addTotalsRow(table, totals);
                }
                document.add(table);
            }
            finishReport(document);
            log.info("PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    /** A leaf line: the first logical column is indented so it reads as belonging to the heading. */
    private void addBranchRow(Table table, String[] rowData, boolean isAlternate) {
        String[] rtlRow = inAddedOrder(rowData);
        int size = style.branchRowSize();
        for (int i = 0; i < rtlRow.length; i++) {
            boolean first = rtl ? i == rtlRow.length - 1 : i == 0;
            Cell cell = paddedAtStart(new Cell()
                    .add(inCell(arabicParagraph(rtlRow[i], size, textWidth(table, i, first ? 20 : 8)), size))
                    .setFontSize(size)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2), first ? 18 : 6);
            table.addCell(striped(cell, isAlternate));
        }
    }

    /** Right-aligned like the rows above it, so each figure sits under the column it sums. */
    private void addBranchSummary(Table table, String[] cells) {
        String[] rtlCells = inAddedOrder(cells);
        for (int i = 0; i < rtlCells.length; i++) {
            String cell = rtlCells[i];
            table.addCell(paddedAtStart(new Cell()
                    .add(inCell(arabicParagraphBold(cell == null ? "" : cell, style.totalsSize(),
                            textWidth(table, i, 8)), style.totalsSize()))
                    .setFontColor(bandTextColor)
                    .setBorderTop(new SolidBorder(bandTextColor, 0.8f))
                    .setBorderBottom(new SolidBorder(bandTextColor, 0.8f))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setFontSize(style.totalsSize())
                    .setPadding(2), 6));
        }
    }

    /**
     * The banded last line, one cell per column - reversed on the way in, exactly as
     * {@link #createTable} reverses the headers and {@link #addTableRow} the rows.
     * <p>
     * It was not, so the line ran left to right under a table that runs right to left: its label
     * printed under the last column and every figure under another column's heading, in every
     * report that has a totals line. The extracted text of such a PDF is correct, so nothing that
     * read the file could see it; {@code PdfExportServiceLayoutTest} reads the positions.
     */
    private void addTotalsRow(Table table, String[] cells) {
        String[] rtlCells = inAddedOrder(cells);
        for (int i = 0; i < rtlCells.length; i++) {
            String cell = rtlCells[i];
            table.addCell(totalsCell(new Cell()
                    .add(inCell(arabicParagraphBold(cell == null ? "" : cell, style.totalsSize(),
                            textWidth(table, i, 4)), style.totalsSize())
                            .setTextAlignment(TextAlignment.CENTER))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(style.totalsSize())
                    .setPadding(2)));
        }
    }

    private void addTotalRow(Table table, String label, String total, int colspan) {
        // The figure sits under the last column: added first in a right-to-left table, last otherwise.
        float[] points = columnPoints.get(table);
        float totalWidth = textWidth(table, rtl || points == null ? 0 : points.length - 1, 4);
        Cell totalCell = totalsCell(new Cell()
                .add(inCell(arabicParagraphBold(total, style.totalsSize(), totalWidth), style.totalsSize())
                        .setTextAlignment(TextAlignment.CENTER))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(style.totalsSize())
                .setPadding(2));
        if (rtl) {
            table.addCell(totalCell);
        }

        // ثم خلية الوصف الممتدة
        Cell labelCell = totalsCell(new Cell(1, colspan)
                .add(inCell(arabicParagraphBold(label, style.totalsSize(),
                        spanWidth(table, 4) - totalWidth - 4 - CELL_SLACK), style.totalsSize())
                        .setTextAlignment(TextAlignment.CENTER))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(style.totalsSize())
                .setPadding(2));
        table.addCell(labelCell);
        if (!rtl) {
            table.addCell(totalCell);
        }
    }

    // ===================== أدوات مساعدة لعكس المصفوفات (RTL) =====================

    private static String[] reverseStrings(String[] arr) {
        if (arr == null) return null;
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[arr.length - 1 - i];
        }
        return out;
    }

    private static float[] reverseFloats(float[] arr) {
        if (arr == null) return null;
        float[] out = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            out[i] = arr[arr.length - 1 - i];
        }
        return out;
    }

    /**
     * إضافة تذييل للمستند، ثم أرقام الصفحات: the closing sentence the style asks for, and every page's
     * number - which can only be written now, when the last page is known.
     */
    private void finishReport(Document document) {
        String footer = setup.footer();
        if (!footer.isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(arabicParagraph(footer, style.smallSize(), usableWidth)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));
        }
        numberPages(document, style.pageNumberSize());
    }

    /**
     * Writes each page's number centred at its foot, or nothing when the style numbers no page.
     * <p>
     * A number written in words is shaped - {@code صفحة 1 من 3} needs it - and placed without a
     * direction of its own: once shaped it is already in the order it is read, and the page's width,
     * not a right edge, is what centres it. <b>A number with no word in it is not shaped.</b> The
     * shaping reads a line with no letter as right to left and reverses its numbers, so {@code 1 / 3}
     * came out {@code 3 / 1} - the first test of this method found it; an invoice has always printed
     * {@code 1 / 3} as written. Each page is measured on its own, since a report may turn one sideways.
     */
    private void numberPages(Document document, float size) {
        PdfDocument pdf = document.getPdfDocument();
        int pages = pdf.getNumberOfPages();
        for (int number = 1; number <= pages; number++) {
            String text = setup.pageText(number, pages);
            if (text.isEmpty()) {
                return;
            }
            String written = text.codePoints().anyMatch(Character::isLetter) ? ArabicTextHelper.shape(text) : text;
            float width = pdf.getPage(number).getPageSize().getWidth();
            document.showTextAligned(new Paragraph(written)
                            .setFont(arabicFont).setFontSize(size).setFontColor(ColorConstants.GRAY),
                    width / 2, PAGE_NUMBER_Y, number, TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0);
        }
    }

    /**
     * تصدير تقرير عام إلى PDF
     */
    public boolean exportGenericReport(
            String filePath,
            String title,
            String subtitle,
            String[] headers,
            float[] columnWidths,
            List<String[]> data,
            String totalLabel,
            String totalValue,
            byte[] chartImageBytes, // الصورة هنا
            PageSize pageSize) {

        try (Document document = createDocument(filePath, pageSize)) {
            addHeader(document, title, subtitle);

            Table table = createTable(headers, columnWidths);

            int rowIndex = 0;
            for (String[] row : data) {
                addTableRow(table, row, rowIndex % 2 == 1);
                rowIndex++;
            }

            if (totalLabel != null && totalValue != null) {
                addTotalRow(table, totalLabel, totalValue, headers.length - 1);
            }

            document.add(table);

            if (chartImageBytes != null) {
                Image chartImage = new Image(ImageDataFactory.create(chartImageBytes));
                chartImage.setAutoScale(true);
                chartImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
                chartImage.setMarginBottom(5f); // مسافة تحت الرسم
                document.add(chartImage);
            }

            finishReport(document);

            log.info("PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    // ===================== a document handed to a customer =====================

    private static final DeviceRgb LABEL_COLOR = new DeviceRgb(236, 240, 241);
    private static final DeviceRgb RULE_COLOR = new DeviceRgb(189, 195, 199);
    private static final float DOCUMENT_FONT_SIZE = 9.5f;
    private static final float DOCUMENT_MARGIN = 24;
    private static final float DOCUMENT_PAGE_NUMBER_SIZE = 8;

    /**
     * An invoice or a return, on the page it was given: the letterhead on the right with the
     * document's name and number opposite it, who it is for, its lines, and a summary box on the
     * left with the notes and a signature line beside it.
     * <p>
     * The page is the one passed and is never turned: a document is upright whatever it holds.
     * The column headings repeat on every page a long document runs onto, the summary is kept
     * whole on the last, and every page is numbered at its foot as the style says.
     * <p>
     * The shop may leave the letterhead off, for paper that already has the company printed at its
     * head, and push the page down under that printed heading ({@link ReportStyle#documentTopSpaceMm}).
     * The document's own name, number and date stay where they are: they are what the paper does not
     * already say.
     * <p>
     * Like every table here, cells are added left to right and each line is reversed on its way
     * in, so the first logical column lands on the right - {@code PdfExportServiceLayoutTest}
     * reads the positions back.
     */
    public boolean exportDocument(String filePath, DocumentPdfPage page, PageSize pageSize) {
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(filePath));
             Document document = new Document(pdf, pageSize, false)) {
            rtl = true;
            document.setFont(arabicFont);
            document.setFontSize(DOCUMENT_FONT_SIZE);
            document.setProperty(Property.BASE_DIRECTION, BaseDirection.RIGHT_TO_LEFT);
            document.setTextAlignment(TextAlignment.RIGHT);
            float top = DOCUMENT_MARGIN
                    + (style.showDocumentLetterhead() ? 0 : style.documentTopSpaceMm() * POINTS_PER_MM);
            document.setMargins(top, DOCUMENT_MARGIN, NUMBERED_FOOT_MARGIN, DOCUMENT_MARGIN);
            usableWidth = pageSize.getWidth() - 2 * DOCUMENT_MARGIN;

            document.add(documentHeader(page));
            document.add(new LineSeparator(rule(1.2f)).setMarginTop(4).setMarginBottom(6));
            if (!page.details().isEmpty()) {
                document.add(documentDetails(page.details()));
            }
            document.add(documentLines(page));
            document.add(documentClosing(page));
            if (!page.footer().isBlank()) {
                document.add(arabicParagraph(page.footer(), 8, usableWidth)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
            }

            numberPages(document, DOCUMENT_PAGE_NUMBER_SIZE);
            log.info("Document PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting document PDF", e);
            return false;
        }
    }

    /**
     * A paragraph that sits in a table cell without the margin above and below it a paragraph
     * otherwise carries, and on a fixed leading. The Naskh face declares a line height far taller
     * than its letters, so a multiplied leading changes little: with the defaults every line of an
     * invoice was more than twice the height of its text, and forty-five lines ran onto three pages.
     */
    private static Paragraph tight(Paragraph paragraph) {
        return paragraph.setMarginTop(0).setMarginBottom(0).setFixedLeading(DOCUMENT_FONT_SIZE * COMPACT_LEADING);
    }

    /** A field's caption on its light grey, unless saving ink. */
    private Cell labelled(Cell cell) {
        return style.inkSaver() ? cell : cell.setBackgroundColor(LABEL_COLOR);
    }
    /** The document's name and number on the left, the letterhead - unless the shop left it off - on the right. */
    private Table documentHeader(DocumentPdfPage page) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();

        Cell identity = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        identity.add(arabicParagraphBold(page.title()).setFontSize(17).setFontColor(headingColor)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));
        if (!page.identity().isEmpty()) {
            Table fields = new Table(UnitValue.createPercentArray(new float[]{55, 45})).useAllAvailableWidth();
            for (DocumentPdfPage.Field field : page.identity()) {
                fields.addCell(new Cell().add(tight(arabicParagraphBold(field.value()))
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(2));
                fields.addCell(labelled(new Cell().add(tight(arabicParagraphBold(field.label())))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(2).setPaddingRight(5)));
            }
            identity.add(fields);
        }
        header.addCell(identity);

        Cell letterhead = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (!style.showDocumentLetterhead()) {
            header.addCell(letterhead);
            return header;
        }
        Div text = new Div();
        text.add(arabicParagraphBold(page.companyName()).setFontSize(15).setMarginBottom(1));
        for (String line : page.companyLines()) {
            text.add(tight(arabicParagraph(line)).setFontSize(8.5f).setFontColor(ColorConstants.DARK_GRAY)
                    .setMarginTop(0).setMarginBottom(0));
        }
        Image logo = logoImage(page.logo());
        if (logo == null) {
            letterhead.add(text);
        } else {
            Table withLogo = new Table(UnitValue.createPercentArray(new float[]{74, 26})).useAllAvailableWidth();
            withLogo.addCell(new Cell().add(text).setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE).setPaddingRight(6));
            withLogo.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE));
            letterhead.add(withLogo);
        }
        header.addCell(letterhead);
        return header;
    }

    /**
     * The company's picture scaled into a small square, or null. A picture the PDF library cannot
     * read prints the letterhead without it rather than failing the whole document.
     */
    private Image logoImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Image logo = new Image(ImageDataFactory.create(bytes));
            logo.scaleToFit(64, 64);
            logo.setHorizontalAlignment(HorizontalAlignment.RIGHT);
            return logo;
        } catch (RuntimeException e) {
            log.warn("The company logo could not be read and was left off the document", e);
            return null;
        }
    }

    /** Two labelled values to a line, the first of each pair on the right. */
    private Table documentDetails(List<DocumentPdfPage.Field> details) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{31, 19, 31, 19})).useAllAvailableWidth();
        table.setMarginBottom(6);
        for (int i = 0; i < details.size(); i += 2) {
            DocumentPdfPage.Field right = details.get(i);
            DocumentPdfPage.Field left = i + 1 < details.size() ? details.get(i + 1) : null;
            addDetail(table, left);
            addDetail(table, right);
        }
        return table;
    }

    private void addDetail(Table table, DocumentPdfPage.Field field) {
        String value = field == null ? "" : field.value();
        String label = field == null ? "" : field.label();
        table.addCell(new Cell().add(tight(arabicParagraph(value)))
                .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(3).setPaddingRight(5));
        Cell labelCell = new Cell().add(tight(arabicParagraphBold(label)))
                .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(3).setPaddingRight(5);
        if (field != null) {
            labelled(labelCell);
        }
        table.addCell(labelCell);
    }

    /**
     * The lines. The first column (the item's name, after the row number) reads right-aligned like
     * text; every other column is a code or a figure and is centred under its heading.
     */
    private Table documentLines(DocumentPdfPage page) {
        String[] headers = reverseStrings(page.headers());
        float[] widths = reverseFloats(page.columnWidths());
        Table table = new Table(UnitValue.createPercentArray(widths))
                .useAllAvailableWidth();
        table.setFont(arabicFont);
        registerColumns(table, widths);
        for (int i = 0; i < headers.length; i++) {
            table.addHeaderCell(headingCell(new Cell()
                    .add(tight(arabicParagraphBold(headers[i], DOCUMENT_FONT_SIZE, textWidth(table, i, 6)))
                            .setTextAlignment(TextAlignment.CENTER))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(3)));
        }
        int columns = headers.length;
        int rowIndex = 0;
        for (String[] row : page.rows()) {
            String[] cells = reverseStrings(row);
            for (int i = 0; i < columns; i++) {
                boolean textColumn = columns - 1 - i == 1;
                Cell cell = new Cell()
                        .add(tight(arabicParagraph(cells[i], DOCUMENT_FONT_SIZE, textWidth(table, i, textColumn ? 7 : 4)))
                                .setTextAlignment(
                                textColumn ? TextAlignment.RIGHT : TextAlignment.CENTER))
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(RULE_COLOR, 0.5f))
                        .setPadding(2).setPaddingRight(textColumn ? 5 : 2);
                table.addCell(striped(cell, rowIndex % 2 == 1));
            }
            rowIndex++;
        }
        if (page.totals() != null) {
            String[] totals = reverseStrings(page.totals());
            for (int i = 0; i < totals.length; i++) {
                table.addCell(bandCell(new Cell()
                        .add(tight(arabicParagraphBold(totals[i], DOCUMENT_FONT_SIZE, textWidth(table, i, 6)))
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.5f))
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setPadding(3)));
            }
        }
        return table;
    }

    /** The summary box on the left; the notes and the signature line on the right. Never split. */
    private Table documentClosing(DocumentPdfPage page) {
        Table closing = new Table(UnitValue.createPercentArray(new float[]{44, 56})).useAllAvailableWidth();
        closing.setMarginTop(8);
        closing.setKeepTogether(true);

        Cell summaryCell = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        if (!page.summary().isEmpty()) {
            Table summary = new Table(UnitValue.createPercentArray(new float[]{48, 52})).useAllAvailableWidth();
            for (DocumentPdfPage.Field field : page.summary()) {
                Paragraph value = field.emphasised()
                        ? tight(arabicParagraphBold(field.value())) : tight(arabicParagraph(field.value()));
                Cell valueCell = new Cell().add(value.setTextAlignment(TextAlignment.CENTER))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(3);
                Cell labelCell = new Cell().add(tight(arabicParagraphBold(field.label())))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(3).setPaddingRight(6);
                if (field.emphasised()) {
                    bandCell(valueCell);
                    bandCell(labelCell);
                } else {
                    labelled(labelCell);
                }
                summary.addCell(valueCell);
                summary.addCell(labelCell);
            }
            summaryCell.add(summary);
        }
        closing.addCell(summaryCell);

        Cell side = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(0).setPaddingLeft(12);
        if (!page.notes().isBlank()) {
            side.add(arabicParagraphBold(page.notesLabel()).setMarginBottom(1));
            // The side cell is 56 of the closing table's hundred, padded 12 on its left.
            side.add(arabicParagraph(page.notes(), 9, usableWidth * 0.56f - 12 - CELL_SLACK).setMarginBottom(10));
        }
        if (!page.signatureLabel().isBlank()) {
            side.add(arabicParagraph(page.signatureLabel() + ": ....................................")
                    .setMarginTop(18));
        }
        closing.addCell(side);
        return closing;
    }
}
