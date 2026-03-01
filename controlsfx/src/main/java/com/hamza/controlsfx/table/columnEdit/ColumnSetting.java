package com.hamza.controlsfx.table.columnEdit;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Callback;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import lombok.extern.log4j.Log4j2;

@SuppressWarnings("unchecked")
@Log4j2
public class ColumnSetting {

    /**
     * Adds a selectable column to the given TableView. This column allows rows
     * to be selected using a checkbox.
     *
     * @param <S>       the type of the TableView's items
     * @param tableView the TableView to which the selectable column is added
     */
    public static <S> void addSelectedColumn(TableView<S> tableView) {
        final ObservableList<TableColumn<S, ?>> columns = tableView.getColumns();
        final TableColumn<S, Boolean> selectableColumn = new TableColumn<>("تحديد");
        selectableColumn.setCellValueFactory(new PropertyValueFactory<>("selectedRow"));
        selectableColumn.setCellFactory(tc -> new CheckBoxTableCell<>());
        columns.addFirst(selectableColumn);
    }

    /**
     * Adds a new column to a specified TableView at a given index, with a provided name
     * and a callback to retrieve the cell's data value.
     *
     * @param <S>       the type of the TableView
     * @param <T>       the type of the TableColumn
     * @param tableView the TableView to which the new column will be added
     * @param name      the name of the new column
     * @param index     the index position in the TableView where the new column will be inserted
     * @param callback  a callback to retrieve the cell's data value for the new column
     */
    public static <S, T> void addColumn(TableView<S> tableView, String name, int index
            , Callback<TableColumn.CellDataFeatures<S, T>, ObservableValue<T>> callback) {
        TableColumn<S, T> column = new TableColumn<>(name);
        column.setCellValueFactory(callback);
        tableView.getColumns().add(index, column);
    }

    /**
     * Enables editing of Double values in the specified column of a TableView.
     *
     * @param <T>         the type of the objects contained within the rows of the table
     * @param columnIndex the index of the column to be edited
     * @param columnEdite a functional interface to handle updates when a cell's value is edited
     * @param tableView   the TableView instance where the column exists
     */
    public <T> void enableDoubleEditing(int columnIndex, TableColumnEdite<T, Double> columnEdite, TableView<T> tableView) {
        TableColumn<T, Double> column = (TableColumn<T, Double>) tableView.getColumns().get(columnIndex);
        configureColumnEditing(column, columnEdite, TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
    }

    /**
     * Enables editing of a specific column in a TableView for String values.
     *
     * @param <T>         the type of the objects contained within the TableView
     * @param columnIndex the index of the column to enable editing on
     * @param columnEdite the handler for updating the column when edits are committed
     * @param tableView   the TableView containing the column to be edited
     */
    public <T> void enableStringEditing(int columnIndex, TableColumnEdite<T, String> columnEdite, TableView<T> tableView) {
        TableColumn<T, String> column = (TableColumn<T, String>) tableView.getColumns().get(columnIndex);
        configureColumnEditing(column, columnEdite, TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
    }

    /**
     * Configures a TableColumn for editing by setting its cell factory and edit commit handler.
     *
     * @param column      the TableColumn to be configured
     * @param columnEdite the implementation of TableColumnEdite to handle the edit commit
     * @param cellFactory the factory to create the custom TableCell
     * @param <S>         the type of the TableColumn's items
     * @param <T>         the type of the TableColumn's cell values
     */
    private <S, T> void configureColumnEditing(TableColumn<S, T> column, TableColumnEdite<S, T> columnEdite,
                                               Callback<TableColumn<S, T>, TableCell<S, T>> cellFactory) {
        column.setCellFactory(cellFactory);
        column.setOnEditCommit(event -> {
            try {
                columnEdite.updateColumn(event);
            } catch (DaoException e) {
                AllAlerts.showExceptionDialog(e);
                log.error(e.getMessage(), e.getCause());
            }
        });
    }
}
