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
import java.util.Locale;
import java.util.Objects;

/** Compact PDF strategy for the immutable administration journal. */
public final class AuditAdminPdfExporter implements AuditAdminExporter {

    private static final String FONT = "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DETAILS_PREVIEW = 300;

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.PDF;
    }

    @Override
    public void export(Path target, AuditAdminExportDocument data) throws IOException {
        PdfFont font = loadFont();
        try (PdfWriter writer = new PdfWriter(target.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A3.rotate())) {
            document.setMargins(20, 20, 20, 20);
            document.setFont(font);
            boolean rtl = LanguageManager.getInstance().isRtl();
            document.setBaseDirection(rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT);
            document.add(paragraph(text("audit.admin.export.report.title"), true, 18, rtl)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(paragraph(text("audit.admin.export.report.range", data.query().from(),
                    data.query().to(), data.rows().size()), false, 10, rtl));
            document.add(paragraph(text("audit.admin.export.pdf.preview.note"), false, 8, rtl));

            String[] headers = {"audit.admin.column.id", "audit.admin.column.time", "audit.admin.column.event",
                    "audit.admin.column.actor", "audit.admin.column.source", "audit.admin.column.workstation",
                    "audit.admin.column.affected", "audit.admin.column.reason", "audit.admin.details.data"};
            Table table = new Table(UnitValue.createPercentArray(new float[]{5, 11, 12, 10, 8, 10, 7, 17, 20}));
            table.setWidth(UnitValue.createPercentValue(100));
            for (String key : headers) table.addHeaderCell(header(text(key), rtl));

            int row = 0;
            for (AuditAdminEvent event : data.rows()) {
                String eventKey = AuditAdminEventLabels.keyFor(event.eventType());
                String eventType = eventKey == null ? event.eventType() : text(eventKey);
                String actor = blank(event.actorName()) ? text("audit.log.actor.system") : event.actorName();
                String workstation = blank(event.workstationName())
                        ? event.workstationId() : event.workstationName();
                String[] values = {String.valueOf(event.id()), DATE_TIME.format(event.occurredAt()), eventType,
                        actor, source(event.source()), workstation, String.valueOf(event.affectedRows()),
                        event.reason(), preview(event.details())};
                for (String value : values) table.addCell(body(Objects.toString(value, ""), row % 2 == 1, rtl));
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
        String safe = Objects.toString(value, "");
        Paragraph paragraph = new Paragraph(rtl ? ArabicTextHelper.shape(safe) : safe).setFontSize(size);
        if (bold) paragraph.setBold();
        return paragraph.setBaseDirection(rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT)
                .setTextAlignment(rtl ? TextAlignment.RIGHT : TextAlignment.LEFT);
    }

    private static PdfFont loadFont() throws IOException {
        try (InputStream input = AuditAdminPdfExporter.class.getResourceAsStream(FONT)) {
            if (input == null) return PdfFontFactory.createFont();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            input.transferTo(bytes);
            return PdfFontFactory.createFont(bytes.toByteArray(), PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        }
    }

    private static String preview(String value) {
        String safe = Objects.toString(value, "");
        return safe.length() <= DETAILS_PREVIEW ? safe : safe.substring(0, DETAILS_PREVIEW) + "…";
    }

    private static String source(String source) {
        return switch (Objects.toString(source, "").toUpperCase(Locale.ROOT)) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> Objects.toString(source, "");
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
