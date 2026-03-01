package com.hamza.account.test;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TableViewIntegratedFilters extends Application {

    private TableView<Person> tableView = new TableView<>();
    private ObservableList<Person> originalData = FXCollections.observableArrayList();
    private FilteredList<Person> filteredData;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        createSampleData();
        filteredData = new FilteredList<>(originalData, p -> true);

        createTableWithIntegratedFilters();

        VBox root = new VBox();
        root.getChildren().addAll(tableView);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("TableView with Integrated Column Filters");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void createTableWithIntegratedFilters() {
        // Name column with integrated filter
        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        setupFilterHeader(nameCol, "name");

        // Age column with integrated filter
        TableColumn<Person, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("age"));
        setupFilterHeader(ageCol, "age");

        // Email column with integrated filter
        TableColumn<Person, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("email"));
        setupFilterHeader(emailCol, "email");

        tableView.getColumns().addAll(nameCol, ageCol, emailCol);
        tableView.setItems(filteredData);
    }

    private void setupFilterHeader(TableColumn<Person, ?> column, String propertyName) {
        TextField filterField = new TextField();
        filterField.setPromptText("Filter " + column.getText() + "...");

        HBox header = new HBox();
        header.getChildren().addAll(new Label(column.getText()), filterField);
        header.setSpacing(5);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        // Set custom header
        column.setGraphic(header);
        column.setText("");

        // Add filter listener
        filterField.textProperty().addListener((observable, oldValue, newValue) -> {
            applyIntegratedFilter();
        });
    }

    private void applyIntegratedFilter() {
        // This is a simplified version - you'd need to track individual filter fields
        filteredData.setPredicate(person -> {
            // Implement your filtering logic here
            // You'll need to store references to all filter fields
            return true;
        });
    }

    private void createSampleData() {
        originalData.addAll(
                new Person("John Doe", 25, "john@example.com"),
                new Person("Jane Smith", 30, "jane@example.com"),
                new Person("Bob Johnson", 35, "bob@example.com")
        );
    }

    // Person model class
    public static class Person {
        private final String name;
        private final int age;
        private final String email;

        public Person(String name, int age, String email) {
            this.name = name;
            this.age = age;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public String getEmail() {
            return email;
        }
    }
}