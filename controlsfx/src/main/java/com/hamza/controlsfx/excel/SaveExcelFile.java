package com.hamza.controlsfx.excel;

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
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for generating and saving Excel files using a user-defined interface.
 */
public class SaveExcelFile {

    /**
     * Generates and downloads an Excel file based on the provided WriteExcelInterface.
     *
     * @param <T>                 Type of data item in the WriteExcelInterface.
     * @param writeExcelInterface Interface that provides methods for generating the Excel file.
     * @return An integer representing the status code of the operation. A returned value of 1 typically indicates success.
     * @throws Exception if an error occurs during the generation or saving of the Excel file.
     */
    public <T> int downLoadExcelFile(WriteExcelInterface<T> writeExcelInterface) throws Exception {
        return saveFile(writeData(sheetRows(writeExcelInterface), writeExcelInterface.sheetName()));
    }

    /**
     * The header, then one row per item, in the order the items were given.
     * <p>
     * <b>They were collected in a {@code TreeMap} keyed by the row number written as a string</b>,
     * and a string sorts {@code "10"} before {@code "2"}. The header was {@code "1"} and the items
     * {@code "2"} onwards, so from the ninth item on the file came out in a different order from
     * the screen - the ninth item written straight under the header, the tenth after it, and so
     * on - in every export in the application that had more than eight rows.
     */
    static <T> List<Object[]> sheetRows(WriteExcelInterface<T> writeExcelInterface) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(writeExcelInterface.columnHeader());
        if (writeExcelInterface.addDataToFile()) {
            for (T t : writeExcelInterface.itemsList()) {
                rows.add(writeExcelInterface.dataRow(t));
            }
        }
        return rows;
    }

    /**
     * One sheet, one spreadsheet row per entry. An empty value is an empty cell:
     * {@code String.valueOf(null)} used to write the word {@code null} into it.
     */
    private XSSFWorkbook writeData(List<Object[]> rows, String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet spreadsheet = workbook.createSheet(sheetName);
        int rowid = 0;

        for (Object[] objectArr : rows) {
            XSSFRow row = spreadsheet.createRow(rowid++);
            int cellid = 0;

            for (Object obj : objectArr) {
                Cell cell = row.createCell(cellid++);
                cell.setCellValue(obj == null ? "" : String.valueOf(obj));
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
        String USER_HOME = System.getProperty("user.home");
        FileChooser fc = new FileChooser();
        File recordsDir = new File(USER_HOME, ".account/records");
        if (!recordsDir.exists()) {
            boolean mkdirs = recordsDir.mkdirs();
        }

        final FileChooser.ExtensionFilter FILTER_XLSX = new FileChooser.ExtensionFilter("data files (*.xlsx)", "*.xlsx");
        fc.getExtensionFilters().add(FILTER_XLSX);

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
