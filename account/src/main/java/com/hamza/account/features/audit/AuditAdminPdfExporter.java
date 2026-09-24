package com.hamza.account.features.audit;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Compact PDF strategy for the immutable administration journal.
 * <p>
 * Printed by {@link PdfExportService} in the shop's report style, as {@link AuditPdfExporter} is and for
 * the same reason; the A3 sheet on its side is kept for its nine columns.
 */
public final class AuditAdminPdfExporter implements AuditAdminExporter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DETAILS_PREVIEW = 300;
    private static final float[] WIDTHS = {5, 11, 12, 10, 8, 10, 7, 17, 20};

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.PDF;
    }

    @Override
    public void export(Path target, AuditAdminExportDocument data) throws IOException {
        String[] headers = {text("audit.admin.column.id"), text("audit.admin.column.time"),
                text("audit.admin.column.event"), text("audit.admin.column.actor"),
                text("audit.admin.column.source"), text("audit.admin.column.workstation"),
                text("audit.admin.column.affected"), text("audit.admin.column.reason"),
                text("audit.admin.details.data")};
        List<String[]> rows = new ArrayList<>(data.rows().size());
        for (AuditAdminEvent event : data.rows()) {
            String eventKey = AuditAdminEventLabels.keyFor(event.eventType());
            String eventType = eventKey == null ? event.eventType() : text(eventKey);
            String actor = blank(event.actorName()) ? text("audit.log.actor.system") : event.actorName();
            String workstation = blank(event.workstationName())
                    ? event.workstationId() : event.workstationName();
            rows.add(new String[]{String.valueOf(event.id()), DATE_TIME.format(event.occurredAt()),
                    Objects.toString(eventType, ""), Objects.toString(actor, ""), source(event.source()),
                    Objects.toString(workstation, ""), String.valueOf(event.affectedRows()),
                    Objects.toString(event.reason(), ""), preview(event.details())});
        }
        String subtitle = text("audit.admin.export.report.range", data.query().from(), data.query().to(),
                data.rows().size()) + "\n" + text("audit.admin.export.pdf.preview.note");
        if (!new PdfExportService().exportGroupedReport(target.toString(), text("audit.admin.export.report.title"),
                subtitle, headers, WIDTHS, rows, null, PageSize.A3.rotate())) {
            throw new IOException("The audit administration PDF could not be written to " + target);
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
