package com.hamza.controlsfx.excel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ReadExcel {

    private final Workbook workbook;

    public ReadExcel(String url) throws IOException {
        InputStream fileIn = new FileInputStream(url);
        workbook = new XSSFWorkbook(fileIn);
    }

    public <T> ObservableList<T> observableListSheet(String nameSheet, DataForSheet<T> dataForSheet) throws IOException {
        ObservableList<T> observableDataList = FXCollections.observableArrayList();
        Sheet sheet = workbook.getSheet(nameSheet);
        DataFormatter dataFormatter = new DataFormatter();
        int i = 0;
        for (Row row : sheet) {
            i++;
            if (i != 1) {
                dataForSheet.dataForSheet(observableDataList, dataFormatter, row);
            }
        }
        workbook.close();
        return observableDataList;
    }

    public ObservableList<String> getSheetNames() {
        ObservableList<String> sheetName = FXCollections.observableArrayList();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            sheetName.add(workbook.getSheetName(i));
        }
        return sheetName;
    }

}
