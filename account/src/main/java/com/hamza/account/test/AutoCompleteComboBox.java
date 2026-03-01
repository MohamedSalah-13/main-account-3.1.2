package com.hamza.account.test;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class AutoCompleteComboBox extends Application {

    @Override
    public void start(Stage primaryStage) {
        ObservableList<String> items = FXCollections.observableArrayList(
                "United States", "United Kingdom", "Canada", "Australia",
                "Germany", "France", "Italy", "Spain", "Japan", "China"
        );

        FilteredList<String> filteredItems = new FilteredList<>(items, s -> true);
        ComboBox<String> comboBox = new ComboBox<>(filteredItems);
        comboBox.setEditable(true);

        comboBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
            if (!comboBox.isShowing()) {
                comboBox.show();
            }

            filteredItems.setPredicate(item -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();
                return item.toLowerCase().contains(lowerCaseFilter);
            });
        });

        // Hide dropdown when focus is lost
        comboBox.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                comboBox.hide();
            }
        });

        VBox root = new VBox(10);
        root.getChildren().add(comboBox);

        Scene scene = new Scene(root, 300, 200);
        primaryStage.setTitle("Auto-complete ComboBox");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}