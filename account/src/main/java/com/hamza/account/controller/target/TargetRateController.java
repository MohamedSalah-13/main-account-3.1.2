package com.hamza.account.controller.target;

import com.hamza.account.openFxml.FxmlPath;
import com.hamza.controlsfx.others.Utils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

@FxmlPath(pathFile = "target/target-rate.fxml")
public class TargetRateController {

    private final DoubleProperty doubleProperty = new SimpleDoubleProperty();
    private final StringProperty targetRateProperty = new SimpleStringProperty();
    private final StringProperty txtRateProperty = new SimpleStringProperty();

    @FXML
    private TextField targetRate, targetAmount, txtRate;

    @FXML
    public void initialize() {
        otherAction();
        setRate();
    }

    public void otherAction() {
        Utils.setTextFormatter(targetRate, targetAmount, txtRate);
        Utils.whenEnterPressed(targetRate, targetAmount, txtRate);
        targetRate.disableProperty().bind(doubleProperty.isEqualTo(0));
        targetAmount.disableProperty().bind(doubleProperty.isEqualTo(0));
        txtRate.disableProperty().bind(doubleProperty.isEqualTo(0));

        targetRate.textProperty().bindBidirectional(targetRateProperty);
        txtRate.textProperty().bindBidirectional(txtRateProperty);
    }

    private void setRate() {
        targetRate.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode().equals(javafx.scene.input.KeyCode.ENTER)) {
                targetAmount.setText(String.valueOf((getDoubleProperty() * Double.parseDouble(targetRate.getText())) / 100));
            }
        });
        targetAmount.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode().equals(javafx.scene.input.KeyCode.ENTER)) {
                targetRate.setText(String.valueOf((Double.parseDouble(targetAmount.getText()) * 100) / getDoubleProperty()));
            }
        });
        targetAmount.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty() && Double.parseDouble(newValue) > getDoubleProperty()) {
                targetAmount.setText(oldValue); // Revert to the previous valid value
            }
        });
        targetRate.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty() && Double.parseDouble(newValue) > 100) {
                targetRate.setText(oldValue); // Revert to the previous valid value
            }
        });

        txtRate.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty() && Double.parseDouble(newValue) > 100) {
                txtRate.setText(oldValue); // Revert to the previous valid value
            }
        });
    }

    public void clearData() {
        txtRate.clear();
        targetAmount.clear();
        targetRate.clear();
    }

    public void setAmount() {
        targetAmount.setText(String.valueOf((getDoubleProperty() * Double.parseDouble(targetRate.getText())) / 100));
    }

    public double getDoubleProperty() {
        return doubleProperty.get();
    }

    public void setDoubleProperty(double doubleProperty) {
        this.doubleProperty.set(doubleProperty);
    }

    public StringProperty targetRatePropertyProperty() {
        return targetRateProperty;
    }

    public StringProperty txtRatePropertyProperty() {
        return txtRateProperty;
    }
}
