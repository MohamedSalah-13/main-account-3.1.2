package com.hamza.account.test;

import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class EnhancedTableViewFilters extends Application {

    private TableView<Person> tableView = new TableView<>();
    private ObservableList<Person> originalData = FXCollections.observableArrayList();
    private FilteredList<Person> filteredData;

    // Filter properties for each column
    private StringProperty nameFilter = new SimpleStringProperty("");
    private StringProperty ageFilter = new SimpleStringProperty("");
    private StringProperty salaryFilter = new SimpleStringProperty("");
    private StringProperty departmentFilter = new SimpleStringProperty("");
    private StringProperty ageOperator = new SimpleStringProperty("=");
    private StringProperty salaryOperator = new SimpleStringProperty("=");

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
        root.setPadding(new Insets(10));
        root.getChildren().addAll(createFilterPanel(), tableView);

        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setTitle("Enhanced TableView with Advanced Filters");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupTableView() {
        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(150);

        TableColumn<Person, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("age"));
        ageCol.setPrefWidth(100);

        TableColumn<Person, Double> salaryCol = new TableColumn<>("Salary");
        salaryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("salary"));
        salaryCol.setPrefWidth(120);
        salaryCol.setCellFactory(col -> new TableCell<Person, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("$%,.2f", item));
                }
            }
        });

        TableColumn<Person, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("department"));
        deptCol.setPrefWidth(120);

        TableColumn<Person, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("email"));
        emailCol.setPrefWidth(200);

        tableView.getColumns().addAll(nameCol, ageCol, salaryCol, deptCol, emailCol);
        tableView.setItems(filteredData);
    }

    private HBox createFilterPanel() {
        HBox filterPanel = new HBox(15);
        filterPanel.setPadding(new Insets(15));
        filterPanel.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1;");

        // Name filter
        VBox nameFilterBox = new VBox(5);
        nameFilterBox.getChildren().addAll(
                new Label("Name:"),
                createFilterField(nameFilter, "Search names...")
        );

        // Age filter with operator
        VBox ageFilterBox = new VBox(5);
        ComboBox<String> ageOpCombo = new ComboBox<>();
        ageOpCombo.getItems().addAll("=", ">", "<", ">=", "<=", "!=");
        ageOpCombo.setValue("=");
        ageOpCombo.valueProperty().bindBidirectional(ageOperator);

        HBox ageBox = new HBox(5);
        ageBox.getChildren().addAll(ageOpCombo, createNumericFilterField(ageFilter, "Age"));
        ageFilterBox.getChildren().addAll(new Label("Age:"), ageBox);

        // Salary filter with operator
        VBox salaryFilterBox = new VBox(5);
        ComboBox<String> salaryOpCombo = new ComboBox<>();
        salaryOpCombo.getItems().addAll("=", ">", "<", ">=", "<=", "!=");
        salaryOpCombo.setValue("=");
        salaryOpCombo.valueProperty().bindBidirectional(salaryOperator);

        HBox salaryBox = new HBox(5);
        salaryBox.getChildren().addAll(salaryOpCombo, createNumericFilterField(salaryFilter, "Salary"));
        salaryFilterBox.getChildren().addAll(new Label("Salary:"), salaryBox);

        // Department filter
        VBox deptFilterBox = new VBox(5);
        ComboBox<String> deptCombo = new ComboBox<>();
        deptCombo.getItems().addAll("All", "Engineering", "Marketing", "HR", "Executive", "Intern");
        deptCombo.setValue("All");
        deptCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            departmentFilter.set(newVal.equals("All") ? "" : newVal);
        });
        deptFilterBox.getChildren().addAll(new Label("Department:"), deptCombo);

        // Clear button
        Button clearBtn = new Button("Clear All");
        clearBtn.setOnAction(e -> clearAllFilters());
        clearBtn.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white;");

        filterPanel.getChildren().addAll(nameFilterBox, ageFilterBox, salaryFilterBox, deptFilterBox, clearBtn);

        return filterPanel;
    }

    private TextField createFilterField(StringProperty property, String prompt) {
        TextField field = new TextField();
        field.textProperty().bindBidirectional(property);
        field.setPromptText(prompt);
        field.setPrefWidth(120);
        return field;
    }

    private TextField createNumericFilterField(StringProperty property, String prompt) {
        TextField field = new TextField();
        field.textProperty().bindBidirectional(property);
        field.setPromptText(prompt);
        field.setPrefWidth(80);

        // Restrict to numbers only
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });

        return field;
    }

    private void setupFilterListeners() {
        nameFilter.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
        ageFilter.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
        salaryFilter.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
        departmentFilter.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
        ageOperator.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
        salaryOperator.addListener((obs, oldVal, newVal) -> applyAdvancedFilter());
    }

    private void applyAdvancedFilter() {
        filteredData.setPredicate(person -> {
            if (person == null) return false;

            // Name filter
            if (!nameFilter.get().isEmpty() &&
                    !person.getName().toLowerCase().contains(nameFilter.get().toLowerCase())) {
                return false;
            }

            // Age filter
            if (!ageFilter.get().isEmpty()) {
                try {
                    int ageValue = Integer.parseInt(ageFilter.get());
                    int personAge = person.getAge();
                    if (!compareNumbers(personAge, ageValue, ageOperator.get())) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // Invalid number, skip this filter
                }
            }

            // Salary filter
            if (!salaryFilter.get().isEmpty()) {
                try {
                    double salaryValue = Double.parseDouble(salaryFilter.get());
                    double personSalary = person.getSalary();
                    if (!compareNumbers(personSalary, salaryValue, salaryOperator.get())) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    // Invalid number, skip this filter
                }
            }

            // Department filter
            if (!departmentFilter.get().isEmpty() &&
                    !person.getDepartment().equalsIgnoreCase(departmentFilter.get())) {
                return false;
            }

            return true;
        });
    }

    private boolean compareNumbers(Number actual, Number filterValue, String operator) {
        double actualVal = actual.doubleValue();
        double filterVal = filterValue.doubleValue();

        switch (operator) {
            case "=":
                return actualVal == filterVal;
            case ">":
                return actualVal > filterVal;
            case "<":
                return actualVal < filterVal;
            case ">=":
                return actualVal >= filterVal;
            case "<=":
                return actualVal <= filterVal;
            case "!=":
                return actualVal != filterVal;
            default:
                return true;
        }
    }

    private void clearAllFilters() {
        nameFilter.set("");
        ageFilter.set("");
        salaryFilter.set("");
        departmentFilter.set("");
        ageOperator.set("=");
        salaryOperator.set("=");
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
                new Person("Tony Stark", 45, 150000.0, "Executive"),
                new Person("Steve Rogers", 32, 85000.0, "Engineering"),
                new Person("Natasha Romanoff", 31, 95000.0, "Executive")
        );
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
