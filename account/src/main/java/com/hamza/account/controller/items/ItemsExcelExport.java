package com.hamza.account.controller.items;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a table to a spreadsheet, column for column.
 * <p>
 * <b>The point of this class is that the heading and the value under it come from the same
 * column.</b> What it replaces built the header row by walking the table's columns and then
 * wrote the values into fixed cell numbers in a different order - so from the third column
 * onwards every heading sat above somebody else's data, and two of the price columns were
 * not exported at all. Nothing failed; the file simply said the wrong thing, which is the
 * worst way for an export to be wrong.
 * <p>
 * Reading the value through {@code getCellData} is also what makes it right whatever the
 * table holds: a column added to the items list is exported by having been added, and one
 * the operator hid is not exported, which is what hiding it meant.
 */
@Log4j2
public final class ItemsExcelExport {

    private ItemsExcelExport() {
    }

    /**
     * Saves the rows currently in {@code tableView}, asking where to put them.
     *
     * @param sheetName    the sheet's name inside the workbook
     * @param initialName  the file name offered in the save dialog
     */
    public static <S> void export(TableView<S> tableView, String sheetName, String initialName) {
        export(tableView, sheetName, initialName, null, null);
    }

    /**
     * As above, with each row's picture placed in a column of its own after the data.
     * <p>
     * After the data, deliberately. The pictures used to be anchored to a fixed column 12
     * whatever the sheet actually held, so they landed on top of real values whenever the
     * table had fewer columns than that - and the column was never widened for them, so
     * they overlapped each other as well.
     *
     * @param image      each row's picture bytes, or {@code null} where it has none
     * @param imageTitle the heading for the picture column
     */
    public static <S> void export(TableView<S> tableView, String sheetName, String initialName,
                                  java.util.function.Function<S, byte[]> image, String imageTitle) {
        List<TableColumn<S, ?>> columns = exportableColumns(tableView);
        if (columns.isEmpty()) return;

        File file = chooseFile(tableView, initialName);
        if (file == null) return;

        // try-with-resources on both: the old version closed the workbook on the happy path
        // only, so any failure while writing leaked it.
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream output = new FileOutputStream(file)) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeader(workbook, sheet, columns);
            writeRows(sheet, tableView, columns);
            for (int index = 0; index < columns.size(); index++) {
                sheet.autoSizeColumn(index);
            }
            if (image != null) {
                writeImages(workbook, sheet, tableView, columns.size(), image, imageTitle);
            }
            workbook.write(output);
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("itemreport.export.success"));
        } catch (IOException e) {
            // No log call beside this one: AllAlerts.handleError is the error boundary and it
            // logs the technical detail behind a reference code. Logging here as well puts the
            // same stack trace in the file twice under two different references.
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.menu.export.excel"), e);
        }
    }

    /**
     * The columns that carry data, in the order they are on screen.
     * <p>
     * A column with no heading is a control rather than data - the row-selection checkbox
     * and the picture - and neither belongs in a spreadsheet. An invisible column is left
     * out because the operator hid it.
     */
    private static <S> List<TableColumn<S, ?>> exportableColumns(TableView<S> tableView) {
        List<TableColumn<S, ?>> columns = new ArrayList<>();
        for (TableColumn<S, ?> column : tableView.getColumns()) {
            if (column.isVisible() && column.getText() != null && !column.getText().isBlank()) {
                columns.add(column);
            }
        }
        return columns;
    }

    private static <S> void writeHeader(Workbook workbook, Sheet sheet, List<TableColumn<S, ?>> columns) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row header = sheet.createRow(0);
        for (int index = 0; index < columns.size(); index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(columns.get(index).getText());
            cell.setCellStyle(style);
        }
    }

    private static <S> void writeRows(Sheet sheet, TableView<S> tableView, List<TableColumn<S, ?>> columns) {
        for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                write(row.createCell(columnIndex), columns.get(columnIndex).getCellData(rowIndex));
            }
        }
    }

    /**
     * Puts each row's picture in the column after the last data column, one row high.
     * <p>
     * A row with no picture is skipped rather than given an empty anchor, and a picture
     * that POI refuses is skipped too: a corrupt image on one item must not cost the
     * operator the whole export.
     */
    private static <S> void writeImages(Workbook workbook, Sheet sheet, TableView<S> tableView,
                                        int column, java.util.function.Function<S, byte[]> image,
                                        String imageTitle) {
        Row header = sheet.getRow(0);
        if (header != null && imageTitle != null) {
            header.createCell(column).setCellValue(imageTitle);
        }
        sheet.setColumnWidth(column, 20 * 256);
        org.apache.poi.ss.usermodel.Drawing<?> drawing = sheet.createDrawingPatriarch();
        org.apache.poi.ss.usermodel.CreationHelper helper = workbook.getCreationHelper();

        for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {
            byte[] bytes = image.apply(tableView.getItems().get(rowIndex));
            if (bytes == null || bytes.length == 0) continue;
            try {
                int picture = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
                org.apache.poi.ss.usermodel.ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(column);
                anchor.setCol2(column + 1);
                anchor.setRow1(rowIndex + 1);
                anchor.setRow2(rowIndex + 2);
                drawing.createPicture(anchor, picture);
            } catch (RuntimeException unreadable) {
                log.warn("Skipped an unreadable item picture on export row {}", rowIndex + 1, unreadable);
            }
        }
    }

    /**
     * A number stays a number so the spreadsheet can add it up; everything else is text.
     * A blank cell is left blank rather than written as the word "null".
     */
    private static void write(Cell cell, Object value) {
        switch (value) {
            case null -> {
            }
            case Number number -> cell.setCellValue(number.doubleValue());
            case Boolean flag -> cell.setCellValue(flag);
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private static <S> File chooseFile(TableView<S> tableView, String initialName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(LanguageManager.getInstance().getString("item.menu.export.excel"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        chooser.setInitialFileName(initialName);
        return chooser.showSaveDialog(tableView.getScene() == null ? null : tableView.getScene().getWindow());
    }
}
