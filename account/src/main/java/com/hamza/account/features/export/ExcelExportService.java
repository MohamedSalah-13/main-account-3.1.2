package com.hamza.account.features.export;

import com.hamza.account.features.inventory.ColumnKind;
import com.hamza.account.features.inventory.InventoryColumn;
import com.hamza.account.features.inventory.InventoryRow;
import com.hamza.account.features.inventory.InventorySummary;
import com.hamza.account.model.domain.DailyItemSales;
import com.hamza.account.model.domain.ItemSalesRank;
import com.hamza.account.model.domain.MonthlySalesViewModel;
import com.hamza.account.model.domain.TableDataReports;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelExportService {

    /**
     * Writes the inventory sheet, driven by the column catalogue rather than a list of
     * headers written out here.
     * <p>
     * Every other export on this class names its columns and reads its fields one by
     * one, so a column added to a screen has to be added again to its export or the
     * two quietly disagree. This one walks {@code columns} - the same list the table is
     * built from - so a column added to {@code InventoryColumns} appears in the file
     * with nothing changed here.
     * <p>
     * Numbers are written as numbers, not as the formatted text on screen: a
     * spreadsheet that cannot sum its own column is not much of an export.
     *
     * @param rows    every row matching the filter, not the page on screen
     * @param columns the leaf columns to write, in order
     * @param summary written as a totals row under the data, so the file says the same
     *                thing the screen's header does
     */
    public void exportInventoryToExcel(List<InventoryRow> rows,
                                       List<InventoryColumn> columns,
                                       InventorySummary summary,
                                       String filePath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("جرد المخزن");
            sheet.setRightToLeft(true);

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            XSSFFont headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            XSSFCellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setBorderTop(BorderStyle.DOUBLE);
            XSSFFont totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);

            // A heading such as شراء spans two columns in the table; the file gets the
            // leaves, so the sub-columns keep their own headers and nothing is lost.
            List<InventoryColumn> leaves = columns.stream()
                    .flatMap(column -> column.leaves().stream())
                    .toList();

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < leaves.size(); i++) {
                Cell cell = headerRow.createCell(i);
                // The table shows "مرتجع\nالمشتريات" over two lines; a spreadsheet
                // header cell should not carry the break.
                cell.setCellValue(leaves.get(i).title().replace('\n', ' '));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (InventoryRow row : rows) {
                Row sheetRow = sheet.createRow(rowNum++);
                for (int i = 0; i < leaves.size(); i++) {
                    InventoryColumn column = leaves.get(i);
                    Cell cell = sheetRow.createCell(i);
                    if (column.kind() == ColumnKind.TEXT) {
                        cell.setCellValue(column.textOf(row));
                    } else {
                        cell.setCellValue(column.numberOf(row));
                    }
                }
            }

            writeTotals(sheet, leaves, summary, rowNum, totalStyle);

            for (int i = 0; i < leaves.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    /**
     * The totals row, written under the column it totals so it lines up with the data.
     * The figures come from the summary rather than from the rows above: the summary is
     * what the database answered for the whole filtered set, and the two must not be
     * allowed to differ.
     */
    private void writeTotals(XSSFSheet sheet, List<InventoryColumn> leaves,
                             InventorySummary summary, int rowNum, XSSFCellStyle style) {
        Row totals = sheet.createRow(rowNum + 1);
        for (int i = 0; i < leaves.size(); i++) {
            Cell cell = totals.createCell(i);
            cell.setCellStyle(style);
            switch (leaves.get(i).id()) {
                case "name" -> cell.setCellValue("الإجمالي (" + summary.itemCount() + " صنف)");
                case "balance" -> cell.setCellValue(summary.totalQuantity());
                case "purchaseValueTotal" -> cell.setCellValue(summary.valueAtCost());
                case "salesValueTotal" -> cell.setCellValue(summary.valueAtSale());
                default -> cell.setBlank();
            }
        }
    }

    public void exportMonthlySalesToExcel(List<MonthlySalesViewModel> data, String filePath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("تقرير المبيعات السنوي");

            // --- 1. إنشاء التنسيقات (Styles) ---
            // تنسيق العنوان (Header)
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // تنسيق البيانات
            XSSFCellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);

            // --- 2. كتابة العناوين ---
            String[] headers = {"السنة", "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر", "الإجمالي"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // --- 3. كتابة البيانات ---
            int rowIdx = 1;
            for (MonthlySalesViewModel item : data) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.getSalesYear());
                row.createCell(1).setCellValue(item.getJanuary().doubleValue());
                row.createCell(2).setCellValue(item.getFebruary().doubleValue());
                row.createCell(3).setCellValue(item.getMarch().doubleValue());
                row.createCell(4).setCellValue(item.getApril().doubleValue());
                row.createCell(5).setCellValue(item.getMay().doubleValue());
                row.createCell(6).setCellValue(item.getJune().doubleValue());
                row.createCell(7).setCellValue(item.getJuly().doubleValue());
                row.createCell(8).setCellValue(item.getAugust().doubleValue());
                row.createCell(9).setCellValue(item.getSeptember().doubleValue());
                row.createCell(10).setCellValue(item.getOctober().doubleValue());
                row.createCell(11).setCellValue(item.getNovember().doubleValue());
                row.createCell(12).setCellValue(item.getDecember().doubleValue());

                Cell totalCell = row.createCell(13);
                totalCell.setCellValue(item.getTotalYearlySales().doubleValue());

                // تطبيق التنسيق على كل الخلايا
                for (int i = 0; i < 14; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            // تلقائي لضبط عرض الأعمدة
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // --- 4. إضافة المخطط البياني (Chart) ---
            drawChart(sheet, data.size());

            // حفظ الملف
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    private void drawChart(XSSFSheet sheet, int dataRows) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        // تحديد مكان الرسم البياني (يبدأ من العمود O والصف 2)
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 15, 1, 25, 20);

        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("مقارنة مبيعات السنوات");
        chart.setTitleOverlay(false);

        XDDFChartLegend legend = chart.getOrAddLegend();
        legend.setPosition(LegendPosition.TOP_RIGHT);

        // المحاور
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("الشهور");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("المبيعات");

        // نطاق الشهور (يناير - ديسمبر) من الرؤوس
        XDDFDataSource<String> months = XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(0, 0, 1, 12));

        XDDFBarChartData chartData = (XDDFBarChartData) chart.createData(ChartTypes.BAR, bottomAxis, leftAxis);
        chartData.setBarDirection(BarDirection.COL);

        // إضافة سلسلة بيانات لكل سنة (كل صف يمثل سنة)
        for (int i = 1; i <= dataRows; i++) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(i, i, 1, 12));
            String yearLabel = sheet.getRow(i).getCell(0).toString();
            XDDFBarChartData.Series series = (XDDFBarChartData.Series) chartData.addSeries(months, values);
            series.setTitle(yearLabel, null);
        }

        chart.plot(chartData);
    }

    // داخل كلاس ExcelExportService.java

    public void exportYearlyReportToExcel(List<TableDataReports> data, String filePath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("التقرير السنوي الشامل");
            sheet.setRightToLeft(true); // ليكون التنسيق من اليمين لليسار

            // تنسيق الرأس (Header Style)
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = workbook.createFont();
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {"الشهر", "المشتريات", "خصم مشتريات", "المبيعات", "خصم مبيعات",
                    "مرتجع مشتريات", "خصم م.مشتريات", "مرتجع مبيعات", "خصم م.مبيعات",
                    "المصروفات", "الربح"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (TableDataReports report : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(report.getReport_month_name());
                row.createCell(1).setCellValue(report.getPurchase());
                row.createCell(2).setCellValue(report.getPurchases_discount());
                row.createCell(3).setCellValue(report.getSales());
                row.createCell(4).setCellValue(report.getSales_discount());
                row.createCell(5).setCellValue(report.getPurchases_return());
                row.createCell(6).setCellValue(report.getPurchases_return_discount());
                row.createCell(7).setCellValue(report.getSales_return());
                row.createCell(8).setCellValue(report.getSales_return_discount());
                row.createCell(9).setCellValue(report.getExpense());
                row.createCell(10).setCellValue(report.getProfit());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    // أضف هذه الدالة داخل كلاس ExcelExportService.java

    public void exportItemSalesToExcel(List<ItemSalesRank> data, String filePath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("الأصناف الأكثر مبيعاً");
            sheet.setRightToLeft(true);

            // تنسيق الرأس
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = workbook.createFont();
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {"اسم الصنف", "الكمية المباعة", "إجمالي المبيعات", "صافي الربح"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (ItemSalesRank item : data) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getItemName());
                row.createCell(1).setCellValue(item.getTotalQty());
                row.createCell(2).setCellValue(item.getTotalAmount());
                row.createCell(3).setCellValue(item.getTotalProfit());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    // إضافة في كلاس ExcelExportService.java
    public void exportDailySalesToExcel(List<DailyItemSales> data, String filePath) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("مبيعات اليوم");
            sheet.setRightToLeft(true);

            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"اسم الصنف", "السعر", "الكمية", "الإجمالي", "رقم الفاتورة", "الوقت"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (DailyItemSales item : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getItemName());
                row.createCell(1).setCellValue(item.getPrice());
                row.createCell(2).setCellValue(item.getQuantity());
                row.createCell(3).setCellValue(item.getTotal());
                row.createCell(4).setCellValue(item.getInvoiceNumber());
                row.createCell(5).setCellValue(item.getInvoiceTime());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }
}