package com.hamza.account.test;

// Java Program to apply different styles
// to a cell in a spreadsheet

// Importing java input/output classes

import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

// Class- for styling cells
public class GFG {

    // Main driver method
    public static void main(String[] args) throws Exception {

        // Create a Work Book
        InputStream fileIn = new FileInputStream("C:\\Users\\Mohamed\\Desktop\\mo.xlsx");
        XSSFWorkbook workbook = new XSSFWorkbook();

        // Step 1: Create a Spread Sheet by
        // creating an object of XSSFSheet
        XSSFSheet spreadsheet = workbook.createSheet("Sheet1");

        // Step 2(a): Creating a row in above XSSFSheet
        // using createRow() method
        XSSFRow row = spreadsheet.createRow((short) 1);

        // Step 2(b): Setting height of a row
        row.setHeight((short) 800);

        // Step 3: Creating an object of type XSSFCell and
        // typecasting above row created to it
        XSSFCell cell = (XSSFCell) row.createCell((short) 1);

        // Step 4: Setting cell values
        cell.setCellValue("Merged cells");

        // Step 5: MERGING CELLS
        // This statement for merging cells

        spreadsheet.addMergedRegion(new CellRangeAddress(
                1, // first row (0-based)
                1, // last row (0-based)
                1, // first column (0-based)
                4 // last column (0-based)
        ));

        // Step 6: CELL Alignment
        row = spreadsheet.createRow(5);

        cell = (XSSFCell) row.createCell(0);
        row.setHeight((short) 800);

        // 6(a) Top Left alignment
        XSSFCellStyle style1 = workbook.createCellStyle();

        spreadsheet.setColumnWidth(0, 8000);
        style1.setAlignment(HorizontalAlignment.CENTER);
        style1.setVerticalAlignment(VerticalAlignment.TOP);

        cell.setCellValue("Hi, I'm top left indent");
        cell.setCellStyle(style1);
        row = spreadsheet.createRow(6);
        cell = (XSSFCell) row.createCell(1);
        row.setHeight((short) 800);

        // 6(b) Center Align Cell Contents
        XSSFCellStyle style2 = workbook.createCellStyle();

        style2.setAlignment(HorizontalAlignment.CENTER);
        style2.setVerticalAlignment(VerticalAlignment.TOP);
        cell.setCellValue("I'm Center Aligned indent");
        cell.setCellStyle(style2);
        row = spreadsheet.createRow(7);
        cell = (XSSFCell) row.createCell(2);
        row.setHeight((short) 800);

        // 6(c) Bottom Right alignment
        XSSFCellStyle style3 = workbook.createCellStyle();

        style3.setAlignment(HorizontalAlignment.RIGHT);
        style3.setVerticalAlignment(VerticalAlignment.BOTTOM);
        cell.setCellValue("I'm Bottom Right indent");
        cell.setCellStyle(style3);
        row = spreadsheet.createRow(8);
        cell = (XSSFCell) row.createCell(3);

        // Step 7: Justifying Alignment
        XSSFCellStyle style4 = workbook.createCellStyle();

        style4.setAlignment(HorizontalAlignment.JUSTIFY);
        style4.setVerticalAlignment(VerticalAlignment.JUSTIFY);
        cell.setCellValue("I'm Justify indent nice to meet you");
        cell.setCellStyle(style4);

        // Step 8: CELL BORDER
        row = spreadsheet.createRow((short) 10);

        row.setHeight((short) 800);
        cell = (XSSFCell) row.createCell((short) 1);
        cell.setCellValue("BORDER");
        XSSFCellStyle style5 = workbook.createCellStyle();

        style5.setBorderBottom(BorderStyle.THICK);
        style5.setBottomBorderColor(IndexedColors.BLUE.getIndex());
        style5.setBorderLeft(BorderStyle.DOUBLE);
        style5.setLeftBorderColor(IndexedColors.GREEN.getIndex());
        style5.setBorderRight(BorderStyle.HAIR);
        style5.setRightBorderColor(IndexedColors.RED.getIndex());
        style5.setBorderTop(BorderStyle.SLANTED_DASH_DOT);
        style5.setTopBorderColor(IndexedColors.BLACK.getIndex());
        cell.setCellStyle(style5);

        // Step 9: Fill Colors
        // 9(a) Background color
        row = spreadsheet.createRow((short) 10);

        cell = (XSSFCell) row.createCell((short) 1);
        XSSFCellStyle style6 = workbook.createCellStyle();

        style6.setFillBackgroundColor(HSSFColor.HSSFColorPredefined.BLUE.getIndex());
        style6.setFillPattern(FillPatternType.THIN_BACKWARD_DIAG);
        style6.setAlignment(HorizontalAlignment.CENTER);

        spreadsheet.setColumnWidth(1, 8000);
        cell.setCellValue("FILL HORIZONTAL CROSS HATCH");
        cell.setCellStyle(style6);

        // 9(b) Foreground color
        row = spreadsheet.createRow((short) 12);
        cell = (XSSFCell) row.createCell((short) 1);

        XSSFCellStyle style7 = workbook.createCellStyle();
        style7.setFillForegroundColor(HSSFColor.HSSFColorPredefined.RED.getIndex());
        style7.setFillPattern(FillPatternType.BIG_SPOTS);
        style7.setAlignment(HorizontalAlignment.FILL);

        cell.setCellValue("THIN VERTICAL STRIPE");
        cell.setCellStyle(style7);

        // Step 10: Creating a new file in the local
        // directory by creating object of FileOutputStream
        FileOutputStream out = new FileOutputStream("C:\\Users\\Mohamed\\Desktop\\mo.xlsx");
        // Step 11: Write to above workbook created in
        // initial step
        workbook.write(out);
        // Step 12: Close the file connection
        out.close();
        // Display message for console window when
        // program is successfully executed
        System.out.println("gfg.xlsx success");
    }
}

