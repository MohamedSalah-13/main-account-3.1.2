package com.hamza.account.features.export;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.*;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.itextpdf.kernel.pdf.PdfName.WritingMode;

/**
 * خدمة تصدير البيانات إلى ملفات PDF
 * تدعم اللغة العربية والتنسيق الاحترافي
 *
 * @author Hamza
 * @version 1.0
 */
@Log4j2
public class PdfExportService {

    private static final String ARABIC_FONT_PATH = "fonts/arial.ttf";
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb ALTERNATE_ROW_COLOR = new DeviceRgb(236, 240, 241);

    private PdfFont arabicFont;
    private PdfFont boldFont;

    public PdfExportService() {
        initializeFonts();
    }

    /**
     * تهيئة الخطوط العربية
     */
    private void initializeFonts() {
        try {
            // محاولة تحميل خط عربي من الموارد
            File fontFile = new File(ARABIC_FONT_PATH);
            if (fontFile.exists()) {
                arabicFont = PdfFontFactory.createFont(ARABIC_FONT_PATH, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED, true);
            } else {
                // استخدام خط افتراضي يدعم العربية
                arabicFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
            }
            boldFont = arabicFont;
            log.info("Arabic fonts loaded successfully");
        } catch (IOException e) {
            log.error("Error loading Arabic fonts", e);
            try {
                // خط احتياطي
                arabicFont = PdfFontFactory.createFont();
                boldFont = arabicFont;
            } catch (IOException ex) {
                log.error("Error loading fallback font", ex);
            }
        }
    }

    /**
     * إنشاء مستند PDF جديد
     */
    private Document createDocument(String filePath, PageSize pageSize) throws IOException {
        PdfWriter writer = new PdfWriter(filePath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, pageSize);
        document.setProperty(Property.RIGHT, WritingMode.getValue());
//        document.setRightToLeft(true); // دعم الكتابة من اليمين لليسار
        document.setMargins(20, 20, 20, 20);
        return document;
    }

    /**
     * إضافة ترويسة للمستند
     */
    private void addHeader(Document document, String title, String subtitle) {
        // العنوان الرئيسي
        Paragraph titlePara = new Paragraph(title)
                .setFont(boldFont)
                .setFontSize(20)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(5);
        document.add(titlePara);
        // العنوان الفرعي
        if (subtitle != null && !subtitle.isEmpty()) {
            Paragraph subtitlePara = new Paragraph(subtitle)
                    .setFont(arabicFont)
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                    .setMarginBottom(20);
            document.add(subtitlePara);
//            LicenseKey.loadLicenseFile("path/to/your/itextkey.xml");
        }

        // التاريخ والوقت
        String dateTime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph datePara = new Paragraph("تاريخ التقرير: " + dateTime)
                .setFont(arabicFont)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.RIGHT)
                .setBaseDirection(BaseDirection.RIGHT_TO_LEFT)
                .setMarginBottom(20);
        document.add(datePara);
    }

    /**
     * إنشاء جدول مع ترويسة
     */
    private Table createTable(String[] headers, float[] columnWidths) {
        Table table = new Table(UnitValue.createPercentArray(columnWidths));
        table.setBaseDirection(BaseDirection.RIGHT_TO_LEFT);
        table.setTextAlignment(TextAlignment.RIGHT);
        table.setWidth(UnitValue.createPercentValue(100));
        // إضافة ترويسة الجدول
        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header)
                            .setFont(boldFont)
                            .setBaseDirection(BaseDirection.RIGHT_TO_LEFT).setTextAlignment(TextAlignment.RIGHT))
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(10)
                    .setBold();
            table.addHeaderCell(cell);
        }

        return table;
    }

    /**
     * إضافة صف للجدول
     */
    private void addTableRow(Table table, String[] rowData, boolean isAlternate) {
        for (String data : rowData) {
            Cell cell = new Cell()
                    .add(new Paragraph(data != null ? data : "").setFont(arabicFont))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(8);

            if (isAlternate) {
                cell.setBackgroundColor(ALTERNATE_ROW_COLOR);
            }

            table.addCell(cell);
        }
    }

    /**
     * إضافة صف إجمالي للجدول
     */
    private void addTotalRow(Table table, String label, String total, int colspan) {
        // خلية الوصف
        Cell labelCell = new Cell(1, colspan)
                .add(new Paragraph(label).setFont(boldFont))
                .setBackgroundColor(new DeviceRgb(52, 152, 219))
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(10)
                .setBold();
        table.addCell(labelCell);

        // خلية المجموع
        Cell totalCell = new Cell()
                .add(new Paragraph(total).setFont(boldFont))
                .setBackgroundColor(new DeviceRgb(52, 152, 219))
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(10)
                .setBold();
        table.addCell(totalCell);
    }

    /**
     * إضافة تذييل للمستند
     */
    private void addFooter(Document document) {
        document.add(new Paragraph("\n"));

        Paragraph footer = new Paragraph("تم إنشاء هذا التقرير بواسطة نظام الحسابات")
                .setFont(arabicFont)
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
            PageSize pageSize) {

        try (Document document = createDocument(filePath, pageSize)) {

            // إضافة الترويسة
            addHeader(document, title, subtitle);

            // إنشاء الجدول
            Table table = createTable(headers, columnWidths);

            // إضافة البيانات
            int rowIndex = 0;
            for (String[] row : data) {
                addTableRow(table, row, rowIndex % 2 == 1);
                rowIndex++;
            }

            // إضافة صف الإجمالي
            if (totalLabel != null && totalValue != null) {
                addTotalRow(table, totalLabel, totalValue, headers.length - 1);
            }

            document.add(table);

            // إضافة التذييل
            addFooter(document);

            log.info("PDF exported successfully: {}", filePath);
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            log.error("Error exporting PDF", e);
            return false;
        }
    }

    /**
     * تصدير فاتورة إلى PDF
     */
    public boolean exportInvoice(InvoiceData invoiceData, String filePath, PageSize pageSize) {
        try (Document document = createDocument(filePath, pageSize)) {

            // معلومات الشركة
            addCompanyInfo(document, invoiceData.getCompanyName(),
                    invoiceData.getCompanyAddress(),
                    invoiceData.getCompanyPhone());

            // معلومات الفاتورة
            addInvoiceInfo(document, invoiceData);

            // جدول الأصناف
            addInvoiceItemsTable(document, invoiceData);

            // الإجماليات
            addInvoiceTotals(document, invoiceData);

            // الملاحظات
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
        Paragraph companyName = new Paragraph(name)
                .setFont(boldFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setBold();
        document.add(companyName);

        if (address != null) {
            Paragraph companyAddress = new Paragraph(address)
                    .setFont(arabicFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(companyAddress);
        }

        if (phone != null) {
            Paragraph companyPhone = new Paragraph("هاتف: " + phone)
                    .setFont(arabicFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(companyPhone);
        }
    }

    private void addInvoiceInfo(Document document, InvoiceData invoiceData) {
        // إنشاء جدول لمعلومات الفاتورة
        Table infoTable = new Table(2);
        infoTable.setWidth(UnitValue.createPercentValue(100));

        addInfoRow(infoTable, "نوع الفاتورة:", invoiceData.getInvoiceType());
        addInfoRow(infoTable, "رقم الفاتورة:", String.valueOf(invoiceData.getInvoiceNumber()));
        addInfoRow(infoTable, "التاريخ:", invoiceData.getInvoiceDate());
        addInfoRow(infoTable, "العميل/المورد:", invoiceData.getCustomerName());

        document.add(infoTable);
        document.add(new Paragraph("\n"));
    }

    private void addInfoRow(Table table, String label, String value) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(boldFont))
                .setBackgroundColor(new DeviceRgb(236, 240, 241))
                .setPadding(5);
        table.addCell(labelCell);

        Cell valueCell = new Cell()
                .add(new Paragraph(value).setFont(arabicFont))
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

        addInfoRow(totalsTable, "الإجمالي:", String.format("%.2f", invoiceData.getSubtotal()));

        if (invoiceData.getDiscount() > 0) {
            addInfoRow(totalsTable, "الخصم:", String.format("%.2f", invoiceData.getDiscount()));
        }

        if (invoiceData.getTax() > 0) {
            addInfoRow(totalsTable, "الضريبة:", String.format("%.2f", invoiceData.getTax()));
        }

        // الصف النهائي
        Cell labelCell = new Cell()
                .add(new Paragraph("الإجمالي النهائي:").setFont(boldFont))
                .setBackgroundColor(HEADER_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBold();
        totalsTable.addCell(labelCell);

        Cell totalCell = new Cell()
                .add(new Paragraph(String.format("%.2f", invoiceData.getTotal())).setFont(boldFont))
                .setBackgroundColor(HEADER_COLOR)
                .setFontColor(ColorConstants.WHITE)
                .setPadding(8)
                .setBold();
        totalsTable.addCell(totalCell);

        document.add(new Paragraph("\n"));
        document.add(totalsTable);
    }

    private void addNotes(Document document, String notes) {
        document.add(new Paragraph("\n"));
        Paragraph notesPara = new Paragraph("ملاحظات:")
                .setFont(boldFont)
                .setBold();
        document.add(notesPara);

        Paragraph notesContent = new Paragraph(notes)
                .setFont(arabicFont)
                .setFontSize(10);
        document.add(notesContent);
    }
}