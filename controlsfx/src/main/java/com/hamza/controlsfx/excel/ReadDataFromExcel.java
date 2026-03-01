package com.hamza.controlsfx.excel;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * A utility class to read data from an Excel file using a specified interface for customization.
 */
@Log4j2
public class ReadDataFromExcel {

    /**
     * Reads data from an Excel file based on the provided {@code ReadExcelInterface} and returns it as a {@code HashMap}.
     *
     * @param <T>                the type of keys maintained by the returned HashMap
     * @param readExcelInterface an implementation of the ReadExcelInterface, which provides necessary metadata
     *                           and callbacks for reading the Excel file.
     * @return a {@code HashMap} where the keys are of type {@code T} and the values are lists containing the data
     * read from the Excel file.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    public <T> HashMap<T, List<?>> readData(ReadExcelInterface<T> readExcelInterface) throws IOException {

        HashMap<T, List<?>> doubleHashMap = new HashMap<>();
        FileInputStream fis = new FileInputStream(readExcelInterface.thePathOfTheFileToBeRead());
        //creating Workbook instance that refers to .xlsx file
        XSSFWorkbook workbook = new XSSFWorkbook(fis);
        //creating a Sheet object to retrieve object
        XSSFSheet sheet = workbook.getSheetAt(readExcelInterface.indexOfSheet());
        DataFormatter dataFormatter = new DataFormatter();

        for (Row row : sheet) {
            if (readExcelInterface.skipFirstRow()) {
                if (row.getRowNum() != 0) {
                    readDataFromRow(readExcelInterface, row, dataFormatter);
                }
            } else {
                readDataFromRow(readExcelInterface, row, dataFormatter);
            }
        }
        // add data to hash list
        readExcelInterface.addLists(doubleHashMap);
        workbook.close();
        return doubleHashMap;
    }

    /**
     * Reads data from an individual Excel row based on the provided ReadExcelInterface and processes the data.
     *
     * @param readExcelInterface the interface containing methods to read and process Excel data
     * @param row                the current row from which data is being read
     * @param dataFormatter      the formatter used for formatting the data in each cell
     */
    private <T> void readDataFromRow(ReadExcelInterface<T> readExcelInterface, Row row, DataFormatter dataFormatter) {
        if (readExcelInterface.betweenRows()) {
            if (row.getRowNum() >= readExcelInterface.minRow() && row.getRowNum() <= readExcelInterface.maxRow()) {
                Iterator<Cell> cellIterator = row.cellIterator();
                readExcelInterface.action(cellIterator, dataFormatter);
            }
        } else {
            Iterator<Cell> cellIterator = row.cellIterator();
            readExcelInterface.action(cellIterator, dataFormatter);
        }
    }
}
