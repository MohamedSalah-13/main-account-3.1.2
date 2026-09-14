package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import com.itextpdf.kernel.geom.PageSize;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel and PDF of the immutable aggregated period result.
 * <p>
 * The PDF is written by {@link PdfExportService}, not by a table of its own. That class already
 * knows the three things a right-to-left report gets wrong - the column order has to be reversed
 * on the way in, numbers have to be isolated from the bidi pass, and the bold face has no minus
 * glyph - and a second writer beside it printed this report with its columns mirrored. Amounts
 * are written the way the screen writes them ({@link Columns#money}); in the spreadsheet they
 * are numbers with a money format, so they can be summed.
 */
public final class ShiftPeriodExportService {

    /** One entry per column, in logical order: the heading key and how wide it prints. */
    static final String[] HEADERS = {
            "user.shift.report.period.column.user", "user.shift.report.period.column.treasury",
            "user.shift.report.period.column.shifts", "user.shift.report.period.column.open",
            "user.shift.report.period.column.closed", "user.shift.report.period.column.sales",
            "user.shift.report.period.column.returns", "user.shift.report.period.column.expenses",
            "user.shift.report.period.column.deposits", "user.shift.report.period.column.withdrawals",
            "user.shift.report.period.column.expected", "user.shift.report.period.column.actual",
            "user.shift.report.period.column.difference", "user.shift.label.invoices.count"
    };
    private static final float[] WIDTHS = {12, 12, 6, 6, 6, 9, 8, 8, 8, 8, 9, 9, 8, 6};

    public void exportExcel(Path target, ShiftPeriodReport report) throws IOException, DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet(text("user.shift.report.period.sheet"));
            sheet.setRightToLeft(LanguageManager.getInstance().isRtl());
            CellStyle header = excelHeader(workbook);
            CellStyle money = workbook.createCellStyle();
            money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            CellStyle count = workbook.createCellStyle();
            count.setDataFormat(workbook.createDataFormat().getFormat("0"));

            Row heading = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                Cell cell = heading.createCell(index);
                cell.setCellValue(text(HEADERS[index]));
                cell.setCellStyle(header);
            }
            int rowIndex = 1;
            for (ShiftPeriodRow value : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                Object[] cells = values(value);
                for (int index = 0; index < cells.length; index++) {
                    Cell cell = row.createCell(index);
                    if (cells[index] instanceof BigDecimal amount) {
                        cell.setCellValue(amount.doubleValue());
                        cell.setCellStyle(money);
                    } else if (cells[index] instanceof Long number) {
                        cell.setCellValue(number);
                        cell.setCellStyle(count);
                    } else {
                        cell.setCellValue(String.valueOf(cells[index]));
                    }
                }
            }
            for (int index = 0; index < HEADERS.length; index++) {
                sheet.setColumnWidth(index, (index < 2 ? 22 : 16) * 256);
            }
            sheet.createFreezePane(0, 1);
            if (rowIndex > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADERS.length - 1));
            }
            workbook.write(output);
        }
    }

    public void exportPdf(Path target, ShiftPeriodReport report) throws IOException, DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        String[] headers = new String[HEADERS.length];
        for (int index = 0; index < HEADERS.length; index++) headers[index] = text(HEADERS[index]);
        boolean written = new PdfExportService().exportGroupedReport(target.toString(),
                text("user.shift.report.period.title"),
                text("user.shift.report.period.range", report.query().from(), report.query().to(),
                        report.rows().size()),
                headers, WIDTHS, pdfRows(report), null, PageSize.A3.rotate());
        if (!written) {
            throw new IOException("Could not write the shift period report to " + target);
        }
    }

    static List<String[]> pdfRows(ShiftPeriodReport report) {
        List<String[]> rows = new ArrayList<>();
        for (ShiftPeriodRow value : report.rows()) {
            Object[] cells = values(value);
            String[] row = new String[cells.length];
            for (int index = 0; index < cells.length; index++) {
                row[index] = cells[index] instanceof BigDecimal amount
                        ? Columns.money(amount) : String.valueOf(cells[index]);
            }
            rows.add(row);
        }
        return rows;
    }

    private static Object[] values(ShiftPeriodRow value) {
        return new Object[]{value.username(), value.treasuryName(), value.shiftCount(),
                value.openShiftCount(), value.closedShiftCount(),
                value.totalSales(), value.totalSalesReturns(), value.totalExpenses(),
                value.totalDeposits(), value.totalWithdrawals(),
                value.totalExpectedBalance(), value.totalActualBalance(),
                value.totalDifference(), value.invoicesCount()};
    }

    private static CellStyle excelHeader(XSSFWorkbook workbook) {
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

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
