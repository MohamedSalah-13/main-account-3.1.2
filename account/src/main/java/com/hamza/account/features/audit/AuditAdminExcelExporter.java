package com.hamza.account.features.audit;

import com.hamza.controlsfx.language.LanguageManager;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Excel strategy for the complete filtered administration journal. */
public final class AuditAdminExcelExporter implements AuditAdminExporter {

    private static final int EXCEL_CELL_LIMIT = 32_767;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.EXCEL;
    }

    @Override
    public void export(Path target, AuditAdminExportDocument document) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet(text("audit.admin.export.sheet"));
            sheet.setRightToLeft(LanguageManager.getInstance().isRtl());
            CellStyle header = headerStyle(workbook);
            CellStyle wrapped = wrappedStyle(workbook);
            writeHeader(sheet.createRow(0), header);

            int rowIndex = 1;
            for (AuditAdminEvent event : document.rows()) {
                writeRow(sheet.createRow(rowIndex++), event, wrapped);
            }
            int[] widths = {12, 22, 23, 20, 16, 22, 16, 42, 60};
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, widths[index] * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, rowIndex - 1, 0, widths.length - 1));
            workbook.write(output);
        }
    }

    private static void writeHeader(Row row, CellStyle style) {
        String[] keys = {"audit.admin.column.id", "audit.admin.column.time", "audit.admin.column.event",
                "audit.admin.column.actor", "audit.admin.column.source", "audit.admin.column.workstation",
                "audit.admin.column.affected", "audit.admin.column.reason", "audit.admin.details.data"};
        for (int index = 0; index < keys.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(text(keys[index]));
            cell.setCellStyle(style);
        }
    }

    private static void writeRow(Row row, AuditAdminEvent event, CellStyle style) {
        String eventKey = AuditAdminEventLabels.keyFor(event.eventType());
        String eventType = eventKey == null ? event.eventType() : text(eventKey);
        String actor = blank(event.actorName()) ? text("audit.log.actor.system") : event.actorName();
        String workstation = blank(event.workstationName()) ? event.workstationId() : event.workstationName();
        String[] values = {String.valueOf(event.id()), DATE_TIME.format(event.occurredAt()), eventType,
                actor, source(event.source()), workstation, String.valueOf(event.affectedRows()),
                event.reason(), event.details()};
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(limit(Objects.toString(values[index], "")));
            cell.setCellStyle(style);
        }
    }

    private static CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle wrappedStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    private static String source(String source) {
        return switch (Objects.toString(source, "").toUpperCase(Locale.ROOT)) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> Objects.toString(source, "");
        };
    }

    private static String limit(String value) {
        return value.length() <= EXCEL_CELL_LIMIT ? value : value.substring(0, EXCEL_CELL_LIMIT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
