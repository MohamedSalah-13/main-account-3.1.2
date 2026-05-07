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
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
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
        return new Paragraph(ArabicTextHelper.shape(text != null ? text : ""))
                .setFont(boldFont)
                .setBold()
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setTextAlignment(TextAlignment.RIGHT);
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

        // العنوان الفرعي
        if (subtitle != null && !subtitle.isEmpty()) {
            Paragraph subtitlePara = arabicParagraph(subtitle)
                    .setFontSize(12)
                    .setMarginBottom(5);
            document.add(subtitlePara);
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

    /**
     * تصدير فاتورة إلى PDF
     */
    public boolean exportInvoice(InvoiceData invoiceData, String filePath, PageSize pageSize) {
        try (Document document = createDocument(filePath, pageSize)) {
            addCompanyInfo(document, invoiceData.getCompanyName(),
                    invoiceData.getCompanyAddress(),
                    invoiceData.getCompanyPhone());
            addInvoiceInfo(document, invoiceData);
            addInvoiceItemsTable(document, invoiceData);
            addInvoiceTotals(document, invoiceData);
            if (invoiceData.getNotes() != null && !invoiceData.getNotes().isEmpty()) {
                addNotes(document, invoiceData.getNotes());
            }
            log.info("Invoice PDF exported successfully: {}", filePath);
            return true;
        } catch (IOException e) {
            log.error("Error exporting invoice PDF", e);
            return false;
        }
    }

    private void addCompanyInfo(Document document, String name, String address, String phone) {
        document.add(arabicParagraphBold(name)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER));

        if (address != null) {
            document.add(arabicParagraph(address)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        if (phone != null) {
            document.add(arabicParagraph("هاتف: " + phone)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20));
        }
    }

    private void addInvoiceInfo(Document document, InvoiceData invoiceData) {
        Table infoTable = new Table(2);
        infoTable.setWidth(UnitValue.createPercentValue(100));
        infoTable.setBaseDirection(BaseDirection.RIGHT_TO_LEFT);

        addInfoRow(infoTable, "نوع الفاتورة:", invoiceData.getInvoiceType());
        addInfoRow(infoTable, "رقم الفاتورة:", String.valueOf(invoiceData.getInvoiceNumber()));
        addInfoRow(infoTable, "التاريخ:", invoiceData.getInvoiceDate());
        addInfoRow(infoTable, "العميل/المورد:", invoiceData.getCustomerName());

        document.add(infoTable);
        document.add(new Paragraph("\n"));
    }

    private void addInfoRow(Table table, String label, String value) {
        Cell labelCell = new Cell()
                .add(arabicParagraphBold(label))
                .setBackgroundColor(new DeviceRgb(236, 240, 241))
                .setPadding(5);
        table.addCell(labelCell);

        Cell valueCell = new Cell()
                .add(arabicParagraph(value))
                .setPadding(5);
        table.addCell(valueCell);
    }

    private void addInvoiceItemsTable(Document document, InvoiceData invoiceData) {
        String[] headers = {"#", "الصنف", "الكمية", "السعر", "الإجمالي"};
        float[] columnWidths = {10, 40, 15, 17.5f, 17.5f};

        Table table = createTable(headers, columnWidths);

        int index = 1;
        for (InvoiceItem item : invoiceData.getItems()) {
            String[] row = {
                    String.valueOf(index++),
                    item.getItemName(),
                    String.format("%.2f", item.getQuantity()),
                    String.format("%.2f", item.getPrice()),
                    String.format("%.2f", item.getTotal())
            };
            addTableRow(table, row, (index - 2) % 2 == 1);
        }

        document.add(table);
    }

    private void addInvoiceTotals(Document document, InvoiceData invoiceData) {
        Table totalsTable = new Table(2);
        totalsTable.setWidth(UnitValue.createPercentValue(40));
        totalsTable.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.LEFT);
        totalsTable.setBaseDirection(BaseDirection.RIGHT_TO_LEFT);

        addInfoRow(totalsTable, "الإجمالي:", String.format("%.2f", invoiceData.getSubtotal()));
        if (invoiceData.getDiscount() > 0) {
            addInfoRow(totalsTable, "الخصم:", String.format("%.2f", invoiceData.getDiscount()));
        }
        if (invoiceData.getTax() > 0) {
            addInfoRow(totalsTable, "الضريبة:", String.format("%.2f", invoiceData.getTax()));
        }

        Cell labelCell = new Cell()
                .add(arabicParagraphBold("الإجمالي النهائي:"))
                .setBackgroundColor(HEADER_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8);
        totalsTable.addCell(labelCell);

        Cell totalCell = new Cell()
                .add(arabicParagraphBold(String.format("%.2f", invoiceData.getTotal())))
                .setBackgroundColor(HEADER_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8);
        totalsTable.addCell(totalCell);

        document.add(new Paragraph("\n"));
        document.add(totalsTable);
    }

    private void addNotes(Document document, String notes) {
        document.add(new Paragraph("\n"));
        document.add(arabicParagraphBold("ملاحظات:"));
        document.add(arabicParagraph(notes).setFontSize(10));
    }
}