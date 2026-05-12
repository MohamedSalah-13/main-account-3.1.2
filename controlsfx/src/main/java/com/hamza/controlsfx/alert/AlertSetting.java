package com.hamza.controlsfx.alert;

import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ImageSetting;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AlertSetting extends Alert {

    private static final List<String> stylesheetPaths = new ArrayList<>();

    public static void setStylesheets(String... stylesheets) {
        stylesheetPaths.clear();
        if (stylesheets == null) return;

        for (String stylesheet : stylesheets) {
            if (stylesheet != null && !stylesheet.isBlank()) {
                stylesheetPaths.add(stylesheet);
            }
        }
    }

    public AlertSetting(AlertType alertType, String message, String header, String title) {
        super(alertType);
        initializeAlert(message, header, title, false);
        showAndWait();
    }

    public AlertSetting(String message, String header, String title) {
        super(AlertType.CONFIRMATION);
        initializeAlert(message, header, title, true);
    }

    private void initializeAlert(String message, String header, String title, boolean b) {
        var dialogPane = getDialogPane();

        dialogPane.getStylesheets().setAll(stylesheetPaths);

        dialogPane.getStyleClass().add("app-root");
        dialogPane.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        setContentText(message);
        setHeaderText(header);
        setTitle(title);
        returnImage(this);

        LanguageManager languageManager = LanguageManager.getInstance();

        buttonOk(this, ButtonType.OK, languageManager.getString("ok"));
        if (b) buttonOk(this, ButtonType.CANCEL, languageManager.getString("cancel"));

        Toolkit.getDefaultToolkit().beep();
    }

    private void returnImage(Alert alert) {
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(new ImageSetting().IMAGE_MINUS));
    }

    private void buttonOk(Alert alert, ButtonType buttonType, String name) {
        Node node = alert.getDialogPane().lookupButton(buttonType);
        if (node instanceof Button button)
            button.setText(name);
    }
}