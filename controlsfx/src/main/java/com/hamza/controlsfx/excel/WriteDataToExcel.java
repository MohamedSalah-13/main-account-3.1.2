package com.hamza.controlsfx.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.Map;
import java.util.Set;

public class WriteDataToExcel {


    public XSSFWorkbook writeData(Map<String, Object[]> studentData, String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet(sheetName);
        XSSFRow row;

        // This data needs to be written (Object[])
        Set<String> keyid = studentData.keySet();
        int rowid = 0;

        // writing the data into the sheets...
        for (String key : keyid) {
            row = spreadsheet.createRow(rowid++);
            Object[] objectArr = studentData.get(key);
            int cellid = 0;

            for (Object obj : objectArr) {
                Cell cell = row.createCell(cellid++);
                cell.setCellValue(String.valueOf(obj));
            }
        }
        return workbook;
    }
}
