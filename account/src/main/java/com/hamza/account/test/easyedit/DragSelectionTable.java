package com.hamza.account.test.easyedit;

//import com.sun.javafx.scene.control.behavior.TableCellBehavior;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.util.Arrays;

public class DragSelectionTable extends Application {

    private final ObservableList<Person> data =
            FXCollections.observableArrayList(
                    new Person("Jacob", "Smith", "jacob.smith@example.com"),
                    new Person("Isabella", "Johnson", "isabella.johnson@example.com"),
                    new Person("Ethan", "Williams", "ethan.williams@example.com"),
                    new Person("Emma", "Jones", "emma.jones@example.com"),
                    new Person("Michael", "Brown", "michael.brown@example.com")
            );
    private TableView<Person> table = new TableView<Person>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(new Group());
        stage.setTitle("Table View Sample");
        stage.setWidth(450);
        stage.setHeight(500);

        final Label label = new Label("Address Book");
        label.setFont(new Font("Arial", 20));

        TableColumn<Person, String> firstNameCol = new TableColumn<>("First Name");
        firstNameCol.setMinWidth(100);
        firstNameCol.setCellValueFactory(new PropertyValueFactory<Person, String>("firstName"));

        TableColumn<Person, String> lastNameCol = new TableColumn<>("Last Name");
        lastNameCol.setMinWidth(100);
        lastNameCol.setCellValueFactory(new PropertyValueFactory<Person, String>("lastName"));

        TableColumn<Person, String> emailCol = new TableColumn<>("Email");
        emailCol.setMinWidth(200);
        emailCol.setCellValueFactory(new PropertyValueFactory<Person, String>("email"));

        final Callback<TableColumn<Person, String>, TableCell<Person, String>> cellFactory = new DragSelectionCellFactory();
        firstNameCol.setCellFactory(cellFactory);
        lastNameCol.setCellFactory(cellFactory);
        emailCol.setCellFactory(cellFactory);

        table.setEditable(true);
        table.setItems(data);
        table.getColumns().addAll(Arrays.asList(firstNameCol, lastNameCol, emailCol));
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.addEventHandler(KeyEvent.KEY_RELEASED, new ControlDownSelectionEventHandler());

        final VBox vbox = new VBox();
        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 0, 0, 10));
        vbox.getChildren().addAll(label, table);

        ((Group) scene.getRoot()).getChildren().addAll(vbox);

        stage.setScene(scene);
        stage.show();
    }

    protected void performSelection(TableView<Person> table, TableColumn<Person, String> column, int index) {
//        final TablePositionBase anchor = TableCellBehavior.getAnchor(table, table.getFocusModel().getFocusedCell());
        final TablePositionBase anchor = null;
        int columnIndex = table.getVisibleLeafIndex(column);

        int minRowIndex = Math.min(anchor.getRow(), index);
        int maxRowIndex = Math.max(anchor.getRow(), index);
        TableColumnBase minColumn = anchor.getColumn() < columnIndex ? anchor.getTableColumn() : column;
        TableColumnBase maxColumn = anchor.getColumn() >= columnIndex ? anchor.getTableColumn() : column;

        table.getSelectionModel().clearSelection();
        final int minColumnIndex = table.getVisibleLeafIndex((TableColumn) minColumn);
        final int maxColumnIndex = table.getVisibleLeafIndex((TableColumn) maxColumn);
        for (int _row = minRowIndex; _row <= maxRowIndex; _row++) {
            for (int _col = minColumnIndex; _col <= maxColumnIndex; _col++) {
                table.getSelectionModel().select(_row, table.getVisibleLeafColumn(_col));
            }
        }
        table.getFocusModel().focus(index, column);
    }

    public static class Person {

        private final SimpleStringProperty firstName;
        private final SimpleStringProperty lastName;
        private final SimpleStringProperty email;

        private Person(String fName, String lName, String email) {
            this.firstName = new SimpleStringProperty(fName);
            this.lastName = new SimpleStringProperty(lName);
            this.email = new SimpleStringProperty(email);
        }

        public String getFirstName() {
            return firstName.get();
        }

        public void setFirstName(String fName) {
            firstName.set(fName);
        }

        public String getLastName() {
            return lastName.get();
        }

        public void setLastName(String fName) {
            lastName.set(fName);
        }

        public String getEmail() {
            return email.get();
        }

        public void setEmail(String fName) {
            email.set(fName);
        }
    }

    public class DragSelectionCellFactory implements Callback<TableColumn<Person, String>, TableCell<Person, String>> {
        @Override
        public TableCell<Person, String> call(final TableColumn<Person, String> col) {
            return new DragSelectionCell();
        }
    }

    public class DragSelectionCell extends TableCell<Person, String> {
        public DragSelectionCell() {
            setOnDragDetected(new DragDetectedEventHandler(this));
            setOnMouseDragEntered(new DragEnteredEventHandler(this));
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
            } else {
                setText(item);
            }
        }

    }

    public class DragDetectedEventHandler implements EventHandler<MouseEvent> {
        TableCell<Person, String> tableCell;

        public DragDetectedEventHandler(TableCell<Person, String> tableCell) {
            this.tableCell = tableCell;
        }

        @Override
        public void handle(final MouseEvent event) {
            tableCell.startFullDrag();
        }
    }

    public class DragEnteredEventHandler implements EventHandler<MouseDragEvent> {
        TableCell<Person, String> tableCell;

        public DragEnteredEventHandler(TableCell<Person, String> tableCell) {
            this.tableCell = tableCell;
        }

        @Override
        public void handle(final MouseDragEvent event) {
            performSelection(tableCell.getTableView(), tableCell.getTableColumn(), tableCell.getIndex());
        }
    }

    public class ControlDownSelectionEventHandler implements EventHandler<KeyEvent> {
        @Override
        public void handle(final KeyEvent event) {
            KeyCode code = event.getCode();
            if (event.isShiftDown() && (KeyCode.UP.equals(code) || KeyCode.DOWN.equals(code) || KeyCode.LEFT.equals(code) || KeyCode.RIGHT.equals(code))) {
                int index = table.getFocusModel().getFocusedCell().getRow();
                TableColumn column = table.getFocusModel().getFocusedCell().getTableColumn();
                performSelection(table, column, index);
            }
        }
    }
}