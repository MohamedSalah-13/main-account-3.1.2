package com.hamza.controlsfx.controller;

import com.hamza.controlsfx.language.StringConstants;
import com.hamza.controlsfx.others.ShowPassService;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import lombok.Getter;

public class PassCheckController {

    @FXML
    private CheckBox checkBox;
    @Getter
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label labelPassword;

    @FXML
    private void initialize() {
        otherSetting();
    }


    private void otherSetting() {
        SimpleBooleanProperty booleanProperty = new SimpleBooleanProperty();
        checkBox.selectedProperty().bindBidirectional(booleanProperty);
        ShowPassService.show(passwordField, booleanProperty);

        passwordField.setPromptText(StringConstants.PLEASE_ENTER_YOUR_SYSTEM_PASSWORD);
        labelPassword.setText(StringConstants.PASSWORD);
        checkBox.setText(StringConstants.SHOW + " " + StringConstants.PASSWORD);

    }
}
