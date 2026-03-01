package com.hamza.account.test;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TableViewWithFilterHeaders extends Application {

    private TableView<Person> tableView = new TableView<>();
    private ObservableList<Person> originalData = FXCollections.observableArrayList();
    private FilteredList<Person> filteredData;

    // Filter properties
    private StringProperty nameFilter = new SimpleStringProperty("");
    private StringProperty ageFilter = new SimpleStringProperty("");
    private StringProperty emailFilter = new SimpleStringProperty("");

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        createSampleData();
        filteredData = new FilteredList<>(originalData);

        setupTableView();
        setupFilterListeners();

        VBox root = new VBox(10);
        root.setPadding(new javafx.geometry.Insets(10));
        root.getChildren().addAll(createFilterHeader(), tableView);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("TableView with Filter Headers");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupTableView() {
        // Name column with filter header
        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));

        // Age column
        TableColumn<Person, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("age"));

        // Email column
        TableColumn<Person, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("email"));

        tableView.getColumns().addAll(nameCol, ageCol, emailCol);
        tableView.setItems(filteredData);
    }

    private HBox createFilterHeader() {
        HBox filterHeader = new HBox(10);
        filterHeader.setPadding(new javafx.geometry.Insets(10));

        TextField nameField = new TextField();
        nameField.setPromptText("Filter name...");
        nameField.textProperty().bindBidirectional(nameFilter);

        TextField ageField = new TextField();
        ageField.setPromptText("Filter age...");
        ageField.textProperty().bindBidirectional(ageFilter);

        TextField emailField = new TextField();
        emailField.setPromptText("Filter email...");
        emailField.textProperty().bindBidirectional(emailFilter);

        // Clear all filters button
        Button clearButton = new Button("Clear Filters");
        clearButton.setOnAction(e -> {
            nameFilter.set("");
            ageFilter.set("");
            emailFilter.set("");
        });

        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox.setHgrow(ageField, Priority.ALWAYS);
        HBox.setHgrow(emailField, Priority.ALWAYS);

        filterHeader.getChildren().addAll(
                new VBox(new Label("Name:"), nameField),
                new VBox(new Label("Age:"), ageField),
                new VBox(new Label("Email:"), emailField),
                clearButton
        );

        return filterHeader;
    }

    private void setupFilterListeners() {
        // Combine all filter properties
        javafx.beans.value.ChangeListener<String> filterListener = (obs, oldVal, newVal) -> applyFilter();

        nameFilter.addListener(filterListener);
        ageFilter.addListener(filterListener);
        emailFilter.addListener(filterListener);
    }

    private void applyFilter() {
        filteredData.setPredicate(person -> {
            if (person == null) return false;

            boolean matchesName = person.getName().toLowerCase()
                    .contains(nameFilter.get().toLowerCase());

            boolean matchesAge = ageFilter.get().isEmpty() ||
                    String.valueOf(person.getAge()).contains(ageFilter.get());

            boolean matchesEmail = person.getEmail().toLowerCase()
                    .contains(emailFilter.get().toLowerCase());

            return matchesName && matchesAge && matchesEmail;
        });
    }

    private void createSampleData() {
        originalData.addAll(
                new Person("John Doe", 25, "john@example.com"),
                new Person("Jane Smith", 30, "jane@example.com"),
                new Person("Bob Johnson", 35, "bob@example.com"),
                new Person("Alice Brown", 28, "alice@example.com"),
                new Person("Charlie Wilson", 32, "charlie@example.com"),
                new Person("Diana Prince", 29, "diana@example.com"),
                new Person("Bruce Wayne", 35, "bruce@example.com")
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
