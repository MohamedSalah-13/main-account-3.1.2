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
import com.itextpdf.layout.properties.*;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * خدمة تصدير البيانات إلى ملفات PDF
 * تدعم اللغة العربية والتنسيق الاحترافي
 *
 * @author Hamza
 * @version 1.1
 */
@Log4j2
public class PdfExportService {

    // الخط يجب أن يكون داخل resources لنتمكن من قراءته من داخل JAR
    private static final String ARABIC_FONT_RESOURCE =
            "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final String ARABIC_FONT_BOLD_RESOURCE =
            "/com/hamza/account/fonts/NotoNaskhArabic-Bold.ttf";

    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb ALTERNATE_ROW_COLOR = new DeviceRgb(236, 240, 241);
    private static final DeviceRgb BRANCH_COLOR = new DeviceRgb(214, 234, 248);
    private static final DeviceRgb BRANCH_TEXT_COLOR = new DeviceRgb(21, 67, 96);

    private PdfFont arabicFont;
    private PdfFont boldFont;

    public PdfExportService() {
        initializeFonts();
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
     */
    private Document createDocument(String filePath, PageSize pageSize) throws IOException {
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, pageSize);
        document.setFont(arabicFont);
        document.setFontSize(11);
        // الاتجاه الافتراضي للمستند كله: من اليمين لليسار
        document.setProperty(Property.BASE_DIRECTION, BaseDirection.RIGHT_TO_LEFT);
        document.setTextAlignment(TextAlignment.RIGHT);
        document.setMargins(20, 20, 20, 20);
        return document;
    }

    /**
     * فقرة عربية جاهزة مع reshaping و bidi
     */
    private Paragraph arabicParagraph(String text) {
        return new Paragraph(ArabicTextHelper.shape(text != null ? text : ""))
                .setFont(arabicFont)
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT);
    }

    private Paragraph arabicParagraphBold(String text) {
        String shaped = ArabicTextHelper.shape(text != null ? text : "");
        return new Paragraph(shaped)
                .setFont(boldFontFor(shaped))
                .setBold()
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT);
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

    /**
     * إضافة ترويسة للمستند
     */
    private void addHeader(Document document, String title, String subtitle) {
        // العنوان الرئيسي
        Paragraph titlePara = arabicParagraphBold(title)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(titlePara);

        // العنوان الفرعي - سطر لكل '\n'. النص يُشكَّل بترتيب العرض قبل أن يلفّه iText، فالفقرة العربية
        // التي تلتف تضع آخرها في السطر الأول وقد تقسم تاريخًا عند شَرطته؛ السطر المقصود يُكتب فقرةً وحده.
        if (subtitle != null && !subtitle.isEmpty()) {
            String[] lines = subtitle.split("\n");
            for (int i = 0; i < lines.length; i++) {
                Paragraph subtitlePara = arabicParagraph(lines[i])
                        .setFontSize(12)
                        .setMarginBottom(i == lines.length - 1 ? 5 : 0);
                document.add(subtitlePara);
            }
        }

        // التاريخ والوقت
        String dateTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph datePara = arabicParagraph("تاريخ التقرير: " + dateTime)
                .setFontSize(10)
                .setMarginBottom(5);
        document.add(datePara);
    }

    /**
     * إنشاء جدول مع ترويسة (الجدول يُبنى من اليمين إلى اليسار بعكس ترتيب الأعمدة)
     */
    private Table createTable(String[] headers, float[] columnWidths) {
        // عكس الأعمدة والعناوين لجعل أول عمود منطقي يظهر في أقصى اليمين
        float[] rtlWidths = reverseFloats(columnWidths);
        String[] rtlHeaders = reverseStrings(headers);

        Table table = new Table(UnitValue.createPercentArray(rtlWidths));
        table.setBaseDirection(BaseDirection.RIGHT_TO_LEFT);
        table.setTextAlignment(TextAlignment.RIGHT);
        table.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
        table.setWidth(UnitValue.createPercentValue(100));
        table.setFont(arabicFont);

        for (String header : rtlHeaders) {
            Cell cell = new Cell()
                    .add(arabicParagraphBold(header).setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2);
            table.addHeaderCell(cell);
        }
        return table;
    }

    /**
     * إضافة صف للجدول مع عكس ترتيب الخلايا (RTL)
     */
    private void addTableRow(Table table, String[] rowData, boolean isAlternate) {
        String[] rtlRow = reverseStrings(rowData);
        for (String data : rtlRow) {
            Cell cell = new Cell()
                    .add(arabicParagraph(data))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2);

            if (isAlternate) {
                cell.setBackgroundColor(ALTERNATE_ROW_COLOR);
            }
            table.addCell(cell);
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
            addFooter(document);
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
                table.addCell(new Cell(1, columns)
                        .add(arabicParagraphBold(branch.title()))
                        .setBackgroundColor(BRANCH_COLOR)
                        .setFontColor(BRANCH_TEXT_COLOR)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setPaddingTop(4)
                        .setPaddingBottom(4)
                        .setPaddingRight(6));
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
            addFooter(document);
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
                    case HEADING -> page.addCell(new Cell(1, columns)
                            .add(arabicParagraphBold(line.cells()[0]))
                            .setBackgroundColor(BRANCH_COLOR)
                            .setFontColor(BRANCH_TEXT_COLOR)
                            .setTextAlignment(TextAlignment.RIGHT)
                            .setVerticalAlignment(VerticalAlignment.MIDDLE)
                            .setPaddingTop(4)
                            .setPaddingBottom(4)
                            .setPaddingRight(6));
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
            addFooter(document);
            log.info("PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    /** A leaf line: the first logical column is indented so it reads as belonging to the heading. */
    private void addBranchRow(Table table, String[] rowData, boolean isAlternate) {
        String[] rtlRow = reverseStrings(rowData);
        for (int i = 0; i < rtlRow.length; i++) {
            boolean first = i == rtlRow.length - 1;
            Cell cell = new Cell()
                    .add(arabicParagraph(rtlRow[i]))
                    .setFontSize(10)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(2)
                    .setPaddingRight(first ? 18 : 6);
            if (isAlternate) {
                cell.setBackgroundColor(ALTERNATE_ROW_COLOR);
            }
            table.addCell(cell);
        }
    }

    /** Right-aligned like the rows above it, so each figure sits under the column it sums. */
    private void addBranchSummary(Table table, String[] cells) {
        for (String cell : reverseStrings(cells)) {
            table.addCell(new Cell()
                    .add(arabicParagraphBold(cell == null ? "" : cell))
                    .setFontColor(BRANCH_TEXT_COLOR)
                    .setBorderTop(new SolidBorder(BRANCH_TEXT_COLOR, 0.8f))
                    .setBorderBottom(new SolidBorder(BRANCH_TEXT_COLOR, 0.8f))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setFontSize(10)
                    .setPadding(2)
                    .setPaddingRight(6));
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
        for (String cell : reverseStrings(cells)) {
            table.addCell(new Cell()
                    .add(arabicParagraphBold(cell == null ? "" : cell)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(new DeviceRgb(52, 152, 219))
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(10)
                    .setPadding(2));
        }
    }

    private void addTotalRow(Table table, String label, String total, int colspan) {
        // خلية المجموع أولاً لتظهر في أقصى اليمين
        Cell totalCell = new Cell()
                .add(arabicParagraphBold(total).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(52, 152, 219))
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setPadding(2);
        table.addCell(totalCell);

        // ثم خلية الوصف الممتدة
        Cell labelCell = new Cell(1, colspan)
                .add(arabicParagraphBold(label).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(52, 152, 219))
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(2);
        table.addCell(labelCell);
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
     * إضافة تذييل للمستند
     */
    private void addFooter(Document document) {
        document.add(new Paragraph("\n"));
        Paragraph footer = arabicParagraph("تم إنشاء هذا التقرير بواسطة نظام الحسابات")
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY);
        document.add(footer);
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

            addFooter(document);

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

    /**
     * An invoice or a return, on the page it was given: the letterhead on the right with the
     * document's name and number opposite it, who it is for, its lines, and a summary box on the
     * left with the notes and a signature line beside it.
     * <p>
     * The page is the one passed and is never turned: a document is upright whatever it holds.
     * The column headings repeat on every page a long document runs onto, the summary is kept
     * whole on the last, and every page is numbered at its foot.
     * <p>
     * Like every table here, cells are added left to right and each line is reversed on its way
     * in, so the first logical column lands on the right - {@code PdfExportServiceLayoutTest}
     * reads the positions back.
     */
    public boolean exportDocument(String filePath, DocumentPdfPage page, PageSize pageSize) {
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(filePath));
             Document document = new Document(pdf, pageSize, false)) {
            document.setFont(arabicFont);
            document.setFontSize(DOCUMENT_FONT_SIZE);
            document.setProperty(Property.BASE_DIRECTION, BaseDirection.RIGHT_TO_LEFT);
            document.setTextAlignment(TextAlignment.RIGHT);
            document.setMargins(24, 24, 34, 24);

            document.add(documentHeader(page));
            document.add(new LineSeparator(new SolidLine(1.2f)).setStrokeColor(HEADER_COLOR)
                    .setMarginTop(4).setMarginBottom(6));
            if (!page.details().isEmpty()) {
                document.add(documentDetails(page.details()));
            }
            document.add(documentLines(page));
            document.add(documentClosing(page));
            if (!page.footer().isBlank()) {
                document.add(arabicParagraph(page.footer()).setFontSize(8)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
            }

            int pages = pdf.getNumberOfPages();
            for (int number = 1; number <= pages; number++) {
                document.showTextAligned(new Paragraph(number + " / " + pages)
                                .setFont(arabicFont).setFontSize(8).setFontColor(ColorConstants.GRAY),
                        pageSize.getWidth() / 2, 16, number,
                        TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0);
            }
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
        return paragraph.setMarginTop(0).setMarginBottom(0).setFixedLeading(DOCUMENT_FONT_SIZE * 1.45f);
    }
    /** The document's name and number on the left, the letterhead on the right. */
    private Table documentHeader(DocumentPdfPage page) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();

        Cell identity = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        identity.add(arabicParagraphBold(page.title()).setFontSize(17).setFontColor(HEADER_COLOR)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(3));
        if (!page.identity().isEmpty()) {
            Table fields = new Table(UnitValue.createPercentArray(new float[]{55, 45})).useAllAvailableWidth();
            for (DocumentPdfPage.Field field : page.identity()) {
                fields.addCell(new Cell().add(tight(arabicParagraphBold(field.value()))
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(2));
                fields.addCell(new Cell().add(tight(arabicParagraphBold(field.label())))
                        .setBackgroundColor(LABEL_COLOR)
                        .setBorder(new SolidBorder(RULE_COLOR, 0.6f)).setPadding(2).setPaddingRight(5));
            }
            identity.add(fields);
        }
        header.addCell(identity);

        Cell letterhead = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
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
            labelCell.setBackgroundColor(LABEL_COLOR);
        }
        table.addCell(labelCell);
    }

    /**
     * The lines. The first column (the item's name, after the row number) reads right-aligned like
     * text; every other column is a code or a figure and is centred under its heading.
     */
    private Table documentLines(DocumentPdfPage page) {
        String[] headers = reverseStrings(page.headers());
        Table table = new Table(UnitValue.createPercentArray(reverseFloats(page.columnWidths())))
                .useAllAvailableWidth();
        table.setFont(arabicFont);
        for (String heading : headers) {
            table.addHeaderCell(new Cell()
                    .add(tight(arabicParagraphBold(heading)).setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(3));
        }
        int columns = headers.length;
        int rowIndex = 0;
        for (String[] row : page.rows()) {
            String[] cells = reverseStrings(row);
            for (int i = 0; i < columns; i++) {
                boolean textColumn = columns - 1 - i == 1;
                Cell cell = new Cell()
                        .add(tight(arabicParagraph(cells[i])).setTextAlignment(
                                textColumn ? TextAlignment.RIGHT : TextAlignment.CENTER))
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setBorder(new SolidBorder(RULE_COLOR, 0.5f))
                        .setPadding(2).setPaddingRight(textColumn ? 5 : 2);
                if (rowIndex % 2 == 1) {
                    cell.setBackgroundColor(ALTERNATE_ROW_COLOR);
                }
                table.addCell(cell);
            }
            rowIndex++;
        }
        if (page.totals() != null) {
            for (String cell : reverseStrings(page.totals())) {
                table.addCell(new Cell()
                        .add(tight(arabicParagraphBold(cell)).setTextAlignment(TextAlignment.CENTER))
                        .setBackgroundColor(BRANCH_COLOR)
                        .setFontColor(BRANCH_TEXT_COLOR)
                        .setBorder(new SolidBorder(RULE_COLOR, 0.5f))
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setPadding(3));
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
                    valueCell.setBackgroundColor(BRANCH_COLOR).setFontColor(BRANCH_TEXT_COLOR);
                    labelCell.setBackgroundColor(BRANCH_COLOR).setFontColor(BRANCH_TEXT_COLOR);
                } else {
                    labelCell.setBackgroundColor(LABEL_COLOR);
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
            side.add(arabicParagraph(page.notes()).setFontSize(9).setMarginBottom(10));
        }
        if (!page.signatureLabel().isBlank()) {
            side.add(arabicParagraph(page.signatureLabel() + ": ....................................")
                    .setMarginTop(18));
        }
        closing.addCell(side);
        return closing;
    }
}
