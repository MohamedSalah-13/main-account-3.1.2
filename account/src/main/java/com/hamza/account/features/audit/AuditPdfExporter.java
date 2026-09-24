package com.hamza.account.features.audit;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact PDF strategy; long JSON stays complete in Excel and is previewed here.
 * <p>
 * <b>It is printed by {@link PdfExportService}, in the shop's report style</b> - its sizes, colours, head
 * and foot, page numbers and letterhead - like every other report. It used to draw its own page, with
 * sizes and a blue of its own, so nothing on the report appearance tab reached it. What it keeps is its
 * sheet: eleven columns want an A3 page on its side, whatever paper the other reports are set to. It
 * still runs the way the reader reads, now because every report does.
 */
public final class AuditPdfExporter implements AuditLogExporter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int JSON_PREVIEW = 300;
    private static final float[] WIDTHS = {5, 11, 9, 7, 9, 7, 8, 9, 10, 13, 13};

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.PDF;
    }

    @Override
    public void export(Path target, AuditExportDocument data) throws IOException {
        String[] headers = {text("audit.log.column.id"), text("audit.log.column.time"),
                text("audit.log.column.actor"), text("audit.log.column.action"), text("audit.log.column.table"),
                text("audit.log.column.record"), text("audit.log.column.source"),
                text("audit.log.column.workstation"), text("audit.log.column.notes"),
                text("audit.log.details.before"), text("audit.log.details.after")};
        List<String[]> rows = new ArrayList<>(data.rows().size());
        for (AuditLogEntry entry : data.rows()) {
            String tableKey = AuditTableLabels.keyFor(entry.tableName());
            String tableName = tableKey == null ? entry.tableName() : text(tableKey);
            String workstation = entry.workstationName().isBlank()
                    ? entry.workstationId() : entry.workstationName();
            rows.add(new String[]{String.valueOf(entry.id()), DATE_TIME.format(entry.actionTime()),
                    entry.actorName(), text(entry.action().labelKey()), tableName, entry.recordId(),
                    source(entry.source()), workstation, entry.notes(), preview(entry.oldData()),
                    preview(entry.newData())});
        }
        String subtitle = text("audit.log.export.report.range", data.query().from(), data.query().to(),
                data.rows().size()) + "\n" + text("audit.log.export.pdf.preview.note");
        if (!new PdfExportService().exportGroupedReport(target.toString(), text("audit.log.export.report.title"),
                subtitle, headers, WIDTHS, rows, null, PageSize.A3.rotate())) {
            throw new IOException("The audit log PDF could not be written to " + target);
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
