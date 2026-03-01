package com.hamza.account.conditional;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

public class TableColorConditionApp extends Application {

    private TableView<Person> tableView;
    private ObservableList<Person> data;
    private List<ColorCondition> conditions = new ArrayList<>();
    private VBox conditionsPanel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Table Color Conditions - Excel Style");

        // Create main layout
        BorderPane mainLayout = new BorderPane();

        // Create control panel
        VBox controlPanel = createControlPanel();

        // Create table
        createTable();

        // Create conditions display panel
        conditionsPanel = new VBox(10);
        conditionsPanel.setPadding(new Insets(10));
        conditionsPanel.setStyle("-fx-border-color: #ccc; -fx-border-width: 1;");

        // Layout setup
        VBox leftPanel = new VBox(10, controlPanel, conditionsPanel);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(300);

        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(tableView);

        Scene scene = new Scene(mainLayout, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private VBox createControlPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-background-color: #f5f5f5;");

        Label titleLabel = new Label("Add Color Condition");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        // Column selection
        Label columnLabel = new Label("Column:");
        ComboBox<String> columnCombo = new ComboBox<>();
        columnCombo.getItems().addAll("Name", "Age", "Email", "Department", "Salary");
        columnCombo.setValue("Age");

        // Condition type
        Label conditionLabel = new Label("Condition:");
        ComboBox<ConditionType> conditionCombo = new ComboBox<>();
        conditionCombo.getItems().addAll(ConditionType.values());
        conditionCombo.setValue(ConditionType.GREATER_THAN);

        // Custom converter for display
        conditionCombo.setConverter(new StringConverter<ConditionType>() {
            @Override
            public String toString(ConditionType condition) {
                return condition.getDisplayName();
            }

            @Override
            public ConditionType fromString(String string) {
                return ConditionType.fromDisplayName(string);
            }
        });

        // Value input
        Label valueLabel = new Label("Value:");
        TextField valueField = new TextField();
        valueField.setPromptText("Enter value...");

        // Apply to selection
        Label applyToLabel = new Label("Apply to:");
        ToggleGroup applyToGroup = new ToggleGroup();
        RadioButton cellButton = new RadioButton("Cell");
        RadioButton rowButton = new RadioButton("Row");
        cellButton.setToggleGroup(applyToGroup);
        rowButton.setToggleGroup(applyToGroup);
        cellButton.setSelected(true);
        HBox applyToBox = new HBox(10, cellButton, rowButton);

        // Color selection
        Label colorLabel = new Label("Color:");
        ComboBox<String> colorCombo = new ComboBox<>();
        colorCombo.getItems().addAll("Light Green", "Light Blue", "Light Yellow", "Light Pink", "Light Coral");
        colorCombo.setValue("Light Green");

        // Add condition button
        Button addButton = new Button("Add Condition");
        addButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addButton.setOnAction(e -> addCondition(
                columnCombo.getValue(),
                conditionCombo.getValue(),
                valueField.getText(),
                cellButton.isSelected() ? "Cell" : "Row",
                colorCombo.getValue()
        ));

        panel.getChildren().addAll(
                titleLabel, columnLabel, columnCombo, conditionLabel, conditionCombo,
                valueLabel, valueField, applyToLabel, applyToBox, colorLabel, colorCombo, addButton
        );

        return panel;
    }

    private void createTable() {
        tableView = new TableView<>();

        // Sample data
        data = FXCollections.observableArrayList(
                new Person("John Doe", 25, "john@example.com", "IT", 50000),
                new Person("Jane Smith", 32, "jane@example.com", "HR", 45000),
                new Person("Bob Johnson", 28, "bob@example.com", "Finance", 60000),
                new Person("Alice Brown", 45, "alice@example.com", "IT", 75000),
                new Person("Charlie Wilson", 22, "charlie@example.com", "Marketing", 40000)
        );

        // Create columns
        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<Person, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(data -> data.getValue().ageProperty().asObject());

        TableColumn<Person, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(data -> data.getValue().emailProperty());

        TableColumn<Person, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(data -> data.getValue().departmentProperty());

        TableColumn<Person, Integer> salaryCol = new TableColumn<>("Salary");
        salaryCol.setCellValueFactory(data -> data.getValue().salaryProperty().asObject());

        tableView.getColumns().addAll(nameCol, ageCol, emailCol, deptCol, salaryCol);
        tableView.setItems(data);

        // Set column widths
        nameCol.setPrefWidth(150);
        ageCol.setPrefWidth(80);
        emailCol.setPrefWidth(200);
        deptCol.setPrefWidth(100);
        salaryCol.setPrefWidth(100);
    }

    private void addCondition(String column, ConditionType condition, String value, String applyTo, String color) {
        if (value.isEmpty()) {
            showAlert("Error", "Please enter a value for the condition.");
            return;
        }

        ColorCondition newCondition = new ColorCondition(column, condition, value, applyTo, color);
        conditions.add(newCondition);

        applyConditionToTable(newCondition);
        updateConditionsPanel();
    }

    private void applyConditionToTable(ColorCondition condition) {
        // Reset all styling first
        tableView.setRowFactory(tv -> new TableRow<Person>() {
            @Override
            protected void updateItem(Person item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    // Apply row-based conditions
                    boolean matches = condition.matches(item);
                    if (matches && "Row".equals(condition.getApplyTo())) {
                        setStyle(getColorStyle(condition.getColor()));
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Apply cell-based conditions to specific columns
//        for (TableColumn<Person, ?> column : tableView.getColumns()) {
//            column.setCellFactory(col -> new TableCell<Person, Object>() {
//                @Override
//                protected void updateItem(Object item, boolean empty) {
//                    super.updateItem(item, empty);
//
//                    if (empty || item == null) {
//                        setStyle("");
//                        setText(null);
//                    } else {
//                        setText(item.toString());
//
//                        Person person = getTableView().getItems().get(getIndex());
//                        boolean matches = condition.matches(person);
//
//                        if (matches && "Cell".equals(condition.getApplyTo()) &&
//                                condition.getColumn().equals(col.getText())) {
//                            setStyle(getColorStyle(condition.getColor()));
//                        } else {
//                            setStyle("");
//                        }
//                    }
//                }
//            });
//        }
    }

    private String getColorStyle(String colorName) {
        switch (colorName) {
            case "Light Green":
                return "-fx-background-color: #90EE90; -fx-border-color: #ccc;";
            case "Light Blue":
                return "-fx-background-color: #ADD8E6; -fx-border-color: #ccc;";
            case "Light Yellow":
                return "-fx-background-color: #FFFFE0; -fx-border-color: #ccc;";
            case "Light Pink":
                return "-fx-background-color: #FFB6C1; -fx-border-color: #ccc;";
            case "Light Coral":
                return "-fx-background-color: #F08080; -fx-border-color: #ccc;";
            default:
                return "-fx-background-color: #90EE90; -fx-border-color: #ccc;";
        }
    }

    private void updateConditionsPanel() {
        conditionsPanel.getChildren().clear();

        Label titleLabel = new Label("Active Conditions:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        conditionsPanel.getChildren().add(titleLabel);

        for (int i = 0; i < conditions.size(); i++) {
            ColorCondition condition = conditions.get(i);
            HBox conditionBox = createConditionBox(condition, i);
            conditionsPanel.getChildren().add(conditionBox);
        }
    }

    private HBox createConditionBox(ColorCondition condition, int index) {
        HBox box = new HBox(10);
        box.setPadding(new Insets(5));
        box.setStyle("-fx-border-color: #eee; -fx-border-width: 1; -fx-background-color: white;");

        Label conditionLabel = new Label(condition.toString());
        conditionLabel.setPrefWidth(200);
        conditionLabel.setWrapText(true);

        Button deleteButton = new Button("Delete");
        deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        deleteButton.setOnAction(e -> removeCondition(index));

        box.getChildren().addAll(conditionLabel, deleteButton);
        return box;
    }

    private void removeCondition(int index) {
        conditions.remove(index);
        // Reapply all remaining conditions
        refreshTableStyling();
        updateConditionsPanel();
    }

    private void refreshTableStyling() {
        // Clear all styling
        tableView.setRowFactory(tv -> new TableRow<Person>() {
            @Override
            protected void updateItem(Person item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle("");
                }
            }
        });

        // Reset cell factories
//        for (TableColumn<Person, ?> column : tableView.getColumns()) {
//            column.setCellFactory(col -> new TableCell<Person, Object>() {
//                @Override
//                protected void updateItem(Object item, boolean empty) {
//                    super.updateItem(item, empty);
//                    if (empty || item == null) {
//                        setStyle("");
//                        setText(null);
//                    } else {
//                        setText(item.toString());
//                        setStyle("");
//                    }
//                }
//            });
//        }

        // Reapply all conditions
        for (ColorCondition condition : conditions) {
            applyConditionToTable(condition);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}