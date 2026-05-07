package com.hamza.account.features.export;

import com.hamza.account.model.domain.MonthlySalesViewModel;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelExportService {

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
}