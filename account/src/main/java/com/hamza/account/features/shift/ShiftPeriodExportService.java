package com.hamza.account.features.shift;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.export.ArabicTextHelper;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.database.DaoException;
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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reusable Excel/PDF writers for the immutable aggregated period result. */
public final class ShiftPeriodExportService {
    private static final String FONT = "/com/hamza/account/fonts/NotoNaskhArabic-Regular.ttf";
    private static final String[] HEADERS = {
            "user.shift.report.period.column.user", "user.shift.report.period.column.treasury",
            "user.shift.report.period.column.shifts", "user.shift.report.period.column.open",
            "user.shift.report.period.column.closed", "user.shift.label.total.sales",
            "user.shift.label.sales.returns", "user.shift.label.expenses",
            "user.shift.label.deposits", "user.shift.label.withdrawals",
            "user.shift.label.expected.balance", "user.shift.report.period.column.actual",
            "user.shift.label.difference", "user.shift.label.invoices.count"
    };

    public void exportExcel(Path target, ShiftPeriodReport report) throws IOException, DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = workbook.createSheet(text("user.shift.report.period.sheet"));
            sheet.setRightToLeft(LanguageManager.getInstance().isRtl());
            CellStyle header = excelHeader(workbook);
            Row heading = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                org.apache.poi.ss.usermodel.Cell cell = heading.createCell(index);
                cell.setCellValue(text(HEADERS[index]));
                cell.setCellStyle(header);
            }
            int rowIndex = 1;
            for (ShiftPeriodRow value : report.rows()) writeExcelRow(sheet.createRow(rowIndex++), value);
            for (int index = 0; index < HEADERS.length; index++) {
                sheet.setColumnWidth(index, (index < 2 ? 22 : 16) * 256);
            }
            sheet.createFreezePane(0, 1);
            if (rowIndex > 1) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, rowIndex - 1, 0, HEADERS.length - 1));
            }
            workbook.write(output);
        }
    }

    public void exportPdf(Path target, ShiftPeriodReport report) throws IOException, DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        PdfFont font = loadFont();
        boolean rtl = LanguageManager.getInstance().isRtl();
        try (PdfWriter writer = new PdfWriter(target.toString());
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf, PageSize.A3.rotate())) {
            document.setMargins(20, 20, 20, 20);
            document.setFont(font);
            document.setBaseDirection(rtl ? BaseDirection.RIGHT_TO_LEFT : BaseDirection.LEFT_TO_RIGHT);
            document.add(paragraph(text("user.shift.report.period.title"), true, 18, rtl)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(paragraph(text("user.shift.report.period.range",
                    report.query().from(), report.query().to(), report.rows().size()), false, 10, rtl));
            Table table = new Table(UnitValue.createPercentArray(
                    new float[]{12, 12, 6, 6, 6, 9, 8, 8, 8, 8, 9, 9, 8, 6}));
            table.setWidth(UnitValue.createPercentValue(100));
            for (String key : HEADERS) table.addHeaderCell(pdfHeader(text(key), rtl));
            int row = 0;
            for (ShiftPeriodRow value : report.rows()) {
                for (String cell : values(value)) table.addCell(pdfBody(cell, row % 2 == 1, rtl));
                row++;
            }
            document.add(table);
        }
    }

    private static void writeExcelRow(Row row, ShiftPeriodRow value) {
        String[] values = values(value);
        for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
    }

    private static String[] values(ShiftPeriodRow value) {
        return new String[]{value.username(), value.treasuryName(), String.valueOf(value.shiftCount()),
                String.valueOf(value.openShiftCount()), String.valueOf(value.closedShiftCount()),
                amount(value.totalSales()), amount(value.totalSalesReturns()), amount(value.totalExpenses()),
                amount(value.totalDeposits()), amount(value.totalWithdrawals()),
                amount(value.totalExpectedBalance()), amount(value.totalActualBalance()),
                amount(value.totalDifference()), String.valueOf(value.invoicesCount())};
    }

    private static String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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

    private static Cell pdfHeader(String value, boolean rtl) {
        return new Cell().add(paragraph(value, true, 7, rtl))
                .setBackgroundColor(new DeviceRgb(41, 128, 185))
                .setFontColor(ColorConstants.WHITE).setPadding(3);
    }

    private static Cell pdfBody(String value, boolean alternate, boolean rtl) {
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
        try (InputStream input = ShiftPeriodExportService.class.getResourceAsStream(FONT)) {
            if (input == null) return PdfFontFactory.createFont();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            input.transferTo(bytes);
            return PdfFontFactory.createFont(bytes.toByteArray(), PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.FORCE_EMBEDDED);
        }
    }

    private static String text(String key, Object... arguments) {
        return arguments.length == 0 ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, arguments);
    }
}
