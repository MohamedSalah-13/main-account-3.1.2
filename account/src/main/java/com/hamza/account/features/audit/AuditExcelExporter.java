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

/** Excel strategy for the complete filtered audit result. */
public final class AuditExcelExporter implements AuditLogExporter {

    private static final int EXCEL_CELL_LIMIT = 32_767;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public AuditExportFormat format() {
        return AuditExportFormat.EXCEL;
    }

    @Override
    public void export(Path target, AuditExportDocument document) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet(text("audit.log.export.sheet"));
            sheet.setRightToLeft(LanguageManager.getInstance().isRtl());
            CellStyle header = headerStyle(workbook);
            CellStyle wrapped = wrappedStyle(workbook);
            writeHeader(sheet.createRow(0), header);

            int rowIndex = 1;
            for (AuditLogEntry entry : document.rows()) {
                writeRow(sheet.createRow(rowIndex++), entry, wrapped);
            }
            int[] widths = {12, 22, 20, 14, 20, 15, 16, 22, 32, 60, 60};
            for (int index = 0; index < widths.length; index++) {
                sheet.setColumnWidth(index, widths[index] * 256);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, widths.length - 1));
            workbook.write(output);
        }
    }

    private static void writeHeader(Row row, CellStyle style) {
        String[] keys = {"audit.log.column.id", "audit.log.column.time", "audit.log.column.actor",
                "audit.log.column.action", "audit.log.column.table", "audit.log.column.record",
                "audit.log.column.source", "audit.log.column.workstation", "audit.log.column.notes",
                "audit.log.details.before", "audit.log.details.after"};
        for (int index = 0; index < keys.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(text(keys[index]));
            cell.setCellStyle(style);
        }
    }

    private static void writeRow(Row row, AuditLogEntry entry, CellStyle style) {
        String tableKey = AuditTableLabels.keyFor(entry.tableName());
        String table = tableKey == null ? entry.tableName() : text(tableKey);
        String actor = entry.actorName().isBlank() ? text("audit.log.actor.unknown") : entry.actorName();
        String workstation = entry.workstationName().isBlank()
                ? entry.workstationId() : entry.workstationName();
        String[] values = {String.valueOf(entry.id()), DATE_TIME.format(entry.actionTime()), actor,
                text(entry.action().labelKey()), table, entry.recordId(), source(entry.source()),
                workstation, entry.notes(), entry.oldData(), entry.newData()};
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellValue(limit(values[index]));
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
        return switch (source.toUpperCase()) {
            case "APP" -> text("audit.log.source.app");
            case "SYSTEM" -> text("audit.log.source.system");
            case "DATABASE" -> text("audit.log.source.database");
            default -> source;
        };
    }

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() <= EXCEL_CELL_LIMIT ? value : value.substring(0, EXCEL_CELL_LIMIT);
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
