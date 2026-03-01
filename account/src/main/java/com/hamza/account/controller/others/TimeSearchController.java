package com.hamza.account.controller.others;

import com.hamza.account.openFxml.FxmlPath;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@FxmlPath(pathFile = "others/time-search.fxml")
public class TimeSearchController {

    @FXML
    private TextField timeFrom, timeTo;
    @FXML
    private Button btnTimeFromPicker, btnTimeToPicker;
//    @FXML
//    private CheckBox checkBoxTimeRange;

    private PopupWindow currentPopup;

    @FXML
    public void initialize() {
        setupTimeFields();

        // Set default time values
        timeFrom.setText("00:00");
        timeTo.setText("23:59");
    }

    private void setupTimeFields() {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        // Add time validation and formatting for timeFrom
        timeFrom.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (!newValue.isEmpty()) {
                    LocalTime.parse(newValue, timeFormatter);
                }
            } catch (DateTimeParseException e) {
                timeFrom.setText(oldValue);
            }
        });

        // Add time validation and formatting for timeTo
        timeTo.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                if (!newValue.isEmpty()) {
                    LocalTime.parse(newValue, timeFormatter);
                }
            } catch (DateTimeParseException e) {
                timeTo.setText(oldValue);
            }
        });

        btnTimeFromPicker.setOnAction(e -> showTimePopup(timeFrom, btnTimeFromPicker));
        btnTimeToPicker.setOnAction(e -> showTimePopup(timeTo, btnTimeToPicker));
    }

    public boolean filterByTimeRange(LocalTime timeEn) {
        LocalTime fromTime = LocalTime.parse(timeFrom.getText());
        LocalTime toTime = LocalTime.parse(timeTo.getText());
        if (timeEn.isBefore(fromTime) || timeEn.isAfter(toTime)) {
            return false;
        }
        return timeEn.isAfter(fromTime) && timeEn.isBefore(toTime);
    }

    private void showTimePopup(TextField timePicker, Button timeButton) {
        if (currentPopup != null && currentPopup.isShowing()) {
            return;
        }

        // Create a custom time selection popup
        currentPopup = new PopupWindow() {
        };

        // Create spinners for hours and minutes
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, LocalTime.now().getHour());
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, LocalTime.now().getMinute());

        // Set spinner formatting
        hourSpinner.setEditable(true);
        minuteSpinner.setEditable(true);

        // Create a layout for the popup
        GridPane popupLayout = new GridPane();
        popupLayout.setHgap(5);
        popupLayout.setVgap(5);
        popupLayout.setPadding(new Insets(10));

        // Add components to the layout
        popupLayout.add(new Label("ساعة:"), 0, 0);
        popupLayout.add(hourSpinner, 1, 0);
        popupLayout.add(new Label("دقيقة:"), 0, 1);
        popupLayout.add(minuteSpinner, 1, 1);

        popupLayout.setStyle("-fx-background-color: #27a7a3 ;" +
                "-fx-border-color: #777");

        // Add a button to apply the selected time
        Button applyButton = new Button("تطبيق");
        applyButton.setOnAction(e -> {
            String formattedTime = String.format("%02d:%02d", hourSpinner.getValue(), minuteSpinner.getValue());
            timePicker.setText(formattedTime);
            currentPopup.hide();
        });

        popupLayout.add(applyButton, 1, 2);

        // Set the content of the popup
        currentPopup.getScene().setRoot(popupLayout);

        // Show the popup below the time button
        currentPopup.show(timeButton.getScene().getWindow(),
                timeButton.localToScreen(timeButton.getBoundsInLocal()).getMinX(),
                timeButton.localToScreen(timeButton.getBoundsInLocal()).getMaxY());

        Stage stage = (Stage) timeButton.getScene().getWindow();
        stage.xProperty().addListener((obs, oldVal, newVal) -> {
            if (currentPopup.isShowing()) {
                currentPopup.setX(timeButton.localToScreen(timeButton.getBoundsInLocal()).getMinX());
                currentPopup.setY(timeButton.localToScreen(timeButton.getBoundsInLocal()).getMaxY());
            }
        });
        stage.yProperty().addListener((obs, oldVal, newVal) -> {
            if (currentPopup.isShowing()) {
                currentPopup.setX(timeButton.localToScreen(timeButton.getBoundsInLocal()).getMinX());
                currentPopup.setY(timeButton.localToScreen(timeButton.getBoundsInLocal()).getMaxY());
            }
        });

        stage.getScene().setOnMouseClicked(event -> {
            if (!currentPopup.isShowing()) return;
            double mouseX = event.getScreenX();
            double mouseY = event.getScreenY();
            if (!currentPopup.getScene().getRoot().getBoundsInParent().contains(mouseX, mouseY)) {
                currentPopup.hide();
            }
        });


    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("تنبيه");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}