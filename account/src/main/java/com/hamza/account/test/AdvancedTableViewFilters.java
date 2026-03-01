package com.hamza.account.test;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class AdvancedTableViewFilters extends Application {

    private TableView<Person> tableView = new TableView<>();
    private ObservableList<Person> originalData = FXCollections.observableArrayList();
    private FilteredList<Person> filteredData;

    // Store filter components for each column
    private Map<String, FilterComponent> filterComponents = new HashMap<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        createSampleData();
        filteredData = new FilteredList<>(originalData, p -> true);

        setupTableView();
        setupGlobalControls();

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.getChildren().addAll(createGlobalControls(), tableView);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setTitle("Advanced TableView with Column Filters");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupTableView() {
        // Name column with text filter
        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        setupTextFilterHeader(nameCol, "name");

        // Age column with numeric filter
        TableColumn<Person, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("age"));
        setupNumericFilterHeader(ageCol, "age");

        // Salary column with numeric filter and comparison operators
        TableColumn<Person, Double> salaryCol = new TableColumn<>("Salary");
        salaryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("salary"));
        setupNumericComparisonFilterHeader(salaryCol, "salary");

        // Department column with text filter
        TableColumn<Person, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("department"));
        setupTextFilterHeader(deptCol, "department");

        tableView.getColumns().addAll(nameCol, ageCol, salaryCol, deptCol);
        tableView.setItems(filteredData);
    }

    private void setupTextFilterHeader(TableColumn<Person, ?> column, String propertyName) {
        TextField filterField = new TextField();
        filterField.setPromptText("Filter " + column.getText() + "...");
        filterField.setPrefWidth(120);

        Button clearBtn = new Button("×");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-weight: bold;");
        clearBtn.setOnAction(e -> filterField.clear());

        HBox filterBox = new HBox(5, filterField, clearBtn);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(5);
        headerBox.getChildren().addAll(new Label(column.getText()), filterBox);

        column.setGraphic(headerBox);
        column.setText("");

        // Store filter component
        FilterComponent filterComp = new FilterComponent(propertyName, FilterType.TEXT);
        filterComp.textFilter = filterField;
        filterComponents.put(propertyName, filterComp);

        // Add listener
        filterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupNumericFilterHeader(TableColumn<Person, ?> column, String propertyName) {
        TextField filterField = new TextField();
        filterField.setPromptText("Filter " + column.getText() + "...");
        filterField.setPrefWidth(120);

        // Add input restriction for numbers only
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                filterField.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        Button clearBtn = new Button("×");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-weight: bold;");
        clearBtn.setOnAction(e -> filterField.clear());

        HBox filterBox = new HBox(5, filterField, clearBtn);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(5);
        headerBox.getChildren().addAll(new Label(column.getText()), filterBox);

        column.setGraphic(headerBox);
        column.setText("");

        // Store filter component
        FilterComponent filterComp = new FilterComponent(propertyName, FilterType.NUMERIC);
        filterComp.textFilter = filterField;
        filterComponents.put(propertyName, filterComp);

        // Add listener
        filterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupNumericComparisonFilterHeader(TableColumn<Person, ?> column, String propertyName) {
        ComboBox<ComparisonOperator> operatorCombo = new ComboBox<>();
        operatorCombo.getItems().addAll(
                ComparisonOperator.EQUALS,
                ComparisonOperator.NOT_EQUALS,
                ComparisonOperator.GREATER_THAN,
                ComparisonOperator.LESS_THAN,
                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                ComparisonOperator.LESS_THAN_OR_EQUAL
        );
        operatorCombo.setValue(ComparisonOperator.EQUALS);
        operatorCombo.setPrefWidth(100);

        TextField valueField = new TextField();
        valueField.setPromptText("Value...");
        valueField.setPrefWidth(80);

        // Add input restriction for numbers only
        valueField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("[\\d.]*")) {
                valueField.setText(newVal.replaceAll("[^\\d.]", ""));
            }
        });

        Button clearBtn = new Button("×");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-weight: bold;");
        clearBtn.setOnAction(e -> valueField.clear());

        HBox filterBox = new HBox(5, operatorCombo, valueField, clearBtn);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        VBox headerBox = new VBox(5);
        headerBox.getChildren().addAll(new Label(column.getText()), filterBox);

        column.setGraphic(headerBox);
        column.setText("");

        // Store filter component
        FilterComponent filterComp = new FilterComponent(propertyName, FilterType.NUMERIC_COMPARISON);
        filterComp.operatorCombo = operatorCombo;
        filterComp.valueField = valueField;
        filterComponents.put(propertyName, filterComp);

        // Add listeners
        operatorCombo.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        valueField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private HBox createGlobalControls() {
        Button clearAllBtn = new Button("Clear All Filters");
        clearAllBtn.setOnAction(e -> clearAllFilters());

        Label resultCount = new Label();
        resultCount.textProperty().bind(
                new SimpleStringProperty("Showing ")
                        .concat(String.valueOf(filteredData.size()))
                        .concat(" of ")
                        .concat(String.valueOf(originalData.size()))
                        .concat(" items")
        );

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getChildren().addAll(clearAllBtn, resultCount);

        return controls;
    }

    private void setupGlobalControls() {
        // Additional setup if needed
    }

    private void applyFilters() {
        filteredData.setPredicate(person -> {
            if (person == null) return false;

            for (FilterComponent filter : filterComponents.values()) {
                if (!matchesFilter(person, filter)) {
                    return false;
                }
            }

            return true;
        });
    }

    private boolean matchesFilter(Person person, FilterComponent filter) {
        try {
            switch (filter.type) {
                case TEXT:
                    return matchesTextFilter(person, filter);
                case NUMERIC:
                    return matchesNumericFilter(person, filter);
                case NUMERIC_COMPARISON:
                    return matchesNumericComparisonFilter(person, filter);
                default:
                    return true;
            }
        } catch (Exception e) {
            return true; // If there's an error in filtering, show the row
        }
    }

    private boolean matchesTextFilter(Person person, FilterComponent filter) {
        String filterText = filter.textFilter.getText().toLowerCase();
        if (filterText.isEmpty()) return true;

        String value = getStringValue(person, filter.propertyName).toLowerCase();
        return value.contains(filterText);
    }

    private boolean matchesNumericFilter(Person person, FilterComponent filter) {
        String filterText = filter.textFilter.getText();
        if (filterText.isEmpty()) return true;

        try {
            double filterValue = Double.parseDouble(filterText);
            double value = getNumericValue(person, filter.propertyName);
            return value == filterValue;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private boolean matchesNumericComparisonFilter(Person person, FilterComponent filter) {
        String valueText = filter.valueField.getText();
        if (valueText.isEmpty()) return true;

        try {
            double filterValue = Double.parseDouble(valueText);
            double value = getNumericValue(person, filter.propertyName);
            ComparisonOperator operator = filter.operatorCombo.getValue();

            switch (operator) {
                case EQUALS:
                    return value == filterValue;
                case NOT_EQUALS:
                    return value != filterValue;
                case GREATER_THAN:
                    return value > filterValue;
                case LESS_THAN:
                    return value < filterValue;
                case GREATER_THAN_OR_EQUAL:
                    return value >= filterValue;
                case LESS_THAN_OR_EQUAL:
                    return value <= filterValue;
                default:
                    return true;
            }
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String getStringValue(Person person, String propertyName) {
        switch (propertyName) {
            case "name":
                return person.getName();
            case "department":
                return person.getDepartment();
            case "email":
                return person.getEmail();
            default:
                return "";
        }
    }

    private double getNumericValue(Person person, String propertyName) {
        switch (propertyName) {
            case "age":
                return person.getAge();
            case "salary":
                return person.getSalary();
            default:
                return 0;
        }
    }

    private void clearAllFilters() {
        for (FilterComponent filter : filterComponents.values()) {
            if (filter.textFilter != null) {
                filter.textFilter.clear();
            }
            if (filter.valueField != null) {
                filter.valueField.clear();
            }
            if (filter.operatorCombo != null) {
                filter.operatorCombo.setValue(ComparisonOperator.EQUALS);
            }
        }
    }

    private void createSampleData() {
        originalData.addAll(
                new Person("John Doe", 25, 50000.0, "Engineering"),
                new Person("Jane Smith", 30, 65000.0, "Marketing"),
                new Person("Bob Johnson", 35, 75000.0, "Engineering"),
                new Person("Alice Brown", 28, 55000.0, "HR"),
                new Person("Charlie Wilson", 32, 80000.0, "Engineering"),
                new Person("Diana Prince", 29, 70000.0, "Marketing"),
                new Person("Bruce Wayne", 35, 120000.0, "Executive"),
                new Person("Clark Kent", 27, 58000.0, "Engineering"),
                new Person("Peter Parker", 24, 45000.0, "Intern"),
                new Person("Tony Stark", 45, 150000.0, "Executive")
        );
    }

    // Enums
    private enum FilterType {
        TEXT, NUMERIC, NUMERIC_COMPARISON
    }

    private enum ComparisonOperator {
        EQUALS("="),
        NOT_EQUALS("≠"),
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_THAN_OR_EQUAL("≥"),
        LESS_THAN_OR_EQUAL("≤");

        private final String symbol;

        ComparisonOperator(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String toString() {
            return symbol;
        }
    }

    // Filter Component Class
    private static class FilterComponent {
        String propertyName;
        FilterType type;
        TextField textFilter;
        ComboBox<ComparisonOperator> operatorCombo;
        TextField valueField;

        FilterComponent(String propertyName, FilterType type) {
            this.propertyName = propertyName;
            this.type = type;
        }
    }

    // Person model class
    public static class Person {
        private final String name;
        private final int age;
        private final double salary;
        private final String department;
        private final String email;

        public Person(String name, int age, double salary, String department) {
            this.name = name;
            this.age = age;
            this.salary = salary;
            this.department = department;
            this.email = name.toLowerCase().replace(" ", ".") + "@company.com";
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        public double getSalary() {
            return salary;
        }

        public String getDepartment() {
            return department;
        }

        public String getEmail() {
            return email;
        }
    }
}
