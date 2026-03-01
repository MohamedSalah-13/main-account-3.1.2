package com.hamza.controlsfx.excel;

import com.hamza.controlsfx.file.Extensions;
import com.hamza.controlsfx.file.FileDir;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A utility class for generating and saving Excel files using a user-defined interface.
 */
public class SaveExcelFile {

    /**
     * Generates and downloads an Excel file based on the provided WriteExcelInterface.
     *
     * @param <T> Type of data item in the WriteExcelInterface.
     * @param writeExcelInterface Interface that provides methods for generating the Excel file.
     * @return An integer representing the status code of the operation. A returned value of 1 typically indicates success.
     * @throws Exception if an error occurs during the generation or saving of the Excel file.
     */
    public <T> int downLoadExcelFile(WriteExcelInterface<T> writeExcelInterface) throws Exception {
        Map<String, Object[]> studentData = new TreeMap<>();
        studentData.put("1", writeExcelInterface.columnHeader());

        if (writeExcelInterface.addDataToFile()) {
            for (T t : writeExcelInterface.itemsList()) {
                studentData.put(String.valueOf(studentData.keySet().size() + 1), writeExcelInterface.dataRow(t));
            }
        }
        return saveFile(writeData(studentData, writeExcelInterface.sheetName()));
    }

    /**
     * Generates an Excel workbook and populates it with the provided student data.
     *
     * @param studentData A map where each key represents a unique identifier (e.g., a student ID)
     *                    and the corresponding value is an array of objects representing a row of
     *                    student data to be written to the Excel sheet.
     * @param sheetName   The name of the sheet in which the student data will be written.
     * @return An XSSFWorkbook object containing the populated Excel sheet.
     */
    private XSSFWorkbook writeData(Map<String, Object[]> studentData, String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet(sheetName);
        Set<String> keyId = studentData.keySet();
        int rowid = 0;

        for (String key : keyId) {
            XSSFRow row = spreadsheet.createRow(rowid++);
            Object[] objectArr = studentData.get(key);
            int cellid = 0;

            for (Object obj : objectArr) {
                Cell cell = row.createCell(cellid++);
                cell.setCellValue(String.valueOf(obj));
            }
        }

        return workbook;
    }

    /**
     * Saves the provided workbook to an Excel file using a file chooser dialog.
     *
     * @param workbook the XSSFWorkbook object representing the Excel workbook to be saved
     * @return 1 if the file was successfully saved, otherwise 0
     * @throws IOException if an I/O error occurs during the file save operation
     */
    private int saveFile(XSSFWorkbook workbook) throws IOException {
        FileChooser fc = new FileChooser();
        File recordsDir = new File(FileDir.USER_HOME, ".account/records");
        if (!recordsDir.exists()) {
            boolean mkdirs = recordsDir.mkdirs();
        }

        fc.getExtensionFilters().add(Extensions.FILTER_XLSX);

        fc.setInitialDirectory(recordsDir);
        String var10000 = String.valueOf(LocalDate.now());
        String nameFile = "ExcelFile-" + var10000 + "-" + LocalTime.now() + ".xlsx";
        nameFile = nameFile.replace(':', '.');
        fc.setInitialFileName(nameFile);


        File dirTo = fc.showSaveDialog(new Stage());
        if (dirTo != null) {
            FileOutputStream out = new FileOutputStream(dirTo);
            workbook.write(out);
            out.close();
            Toolkit.getDefaultToolkit().beep();
            return 1;
        }
        return 0;
    }
}
