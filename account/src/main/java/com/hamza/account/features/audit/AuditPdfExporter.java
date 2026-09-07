package com.hamza.account.features.audit;

import com.hamza.account.features.export.ArabicTextHelper;
import com.hamza.controlsfx.language.LanguageManager;
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
import com.itextpdf.layout.properties.BaseDirection;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/** Compact PDF strategy; long JSON stays complete in Excel and is previewed here. */
public final class AuditPdfExporter implements AuditLogExporter {

    private static final String FONT = "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int JSON_PREVIEW = 300;

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.PDF;
    }

    @Override
    public void export(Path target, AuditExportDocument data) throws IOException {
        PdfFont font = loadFont();
        try (PdfWriter writer = new PdfWriter(target.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A3.rotate())) {
            document.setMargins(20, 20, 20, 20);
            document.setFont(font);
            boolean rtl = LanguageManager.getInstance().isRtl();
            document.setBaseDirection(rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT);
            document.add(paragraph(text("audit.log.export.report.title"), true, 18, rtl)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(paragraph(text("audit.log.export.report.range", data.query().from(), data.query().to(),
                    data.rows().size()), false, 10, rtl));
            document.add(paragraph(text("audit.log.export.pdf.preview.note"), false, 8, rtl));

            String[] headers = {"audit.log.column.id", "audit.log.column.time", "audit.log.column.actor",
                    "audit.log.column.action", "audit.log.column.table", "audit.log.column.record",
                    "audit.log.column.source", "audit.log.column.workstation", "audit.log.column.notes",
                    "audit.log.details.before", "audit.log.details.after"};
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{5, 11, 9, 7, 9, 7, 8, 9, 10, 13, 13}));
            table.setWidth(UnitValue.createPercentValue(100));
            for (String key : headers) table.addHeaderCell(header(text(key), rtl));
            int row = 0;
            for (AuditLogEntry entry : data.rows()) {
                String tableKey = AuditTableLabels.keyFor(entry.tableName());
                String tableName = tableKey == null ? entry.tableName() : text(tableKey);
                String workstation = entry.workstationName().isBlank()
                        ? entry.workstationId() : entry.workstationName();
                String[] values = {String.valueOf(entry.id()), DATE_TIME.format(entry.actionTime()),
                        entry.actorName(), text(entry.action().labelKey()), tableName, entry.recordId(),
                        source(entry.source()), workstation, entry.notes(), preview(entry.oldData()),
                        preview(entry.newData())};
                for (String value : values) table.addCell(body(value, row % 2 == 1, rtl));
                row++;
            }
            document.add(table);
        }
    }

    private static Cell header(String value, boolean rtl) {
        return new Cell().add(paragraph(value, true, 8, rtl))
                .setBackgroundColor(new DeviceRgb(41, 128, 185))
                .setFontColor(ColorConstants.WHITE)
                .setPadding(3);
    }

    private static Cell body(String value, boolean alternate, boolean rtl) {
        Cell cell = new Cell().add(paragraph(value, false, 7, rtl)).setPadding(2);
        if (alternate) cell.setBackgroundColor(new DeviceRgb(242, 246, 248));
        return cell;
    }

    private static Paragraph paragraph(String value, boolean bold, float size, boolean rtl) {
        String safe = value == null ? "" : value;
        Paragraph paragraph = new Paragraph(rtl ? ArabicTextHelper.shape(safe) : safe).setFontSize(size);
        if (bold) paragraph.setBold();
        return paragraph.setBaseDirection(rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT)
                .setTextAlignment(rtl ? TextAlignment.RIGHT : TextAlignment.LEFT);
    }

    private static PdfFont loadFont() throws IOException {
        try (InputStream input = AuditPdfExporter.class.getResourceAsStream(FONT)) {
            if (input == null) return PdfFontFactory.createFont();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            input.transferTo(bytes);
            return PdfFontFactory.createFont(bytes.toByteArray(), PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        }
    }

    private static String preview(String value) {
        if (value == null) return "";
        return value.length() <= JSON_PREVIEW ? value : value.substring(0, JSON_PREVIEW) + "…";
    }

    private static String source(String source) {
        return switch (source.toUpperCase()) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> source;
        };
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
