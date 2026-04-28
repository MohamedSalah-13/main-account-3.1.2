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

public class AlertSetting extends Alert {

    public static String stylesheetPath;

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

        if (stylesheetPath != null)
            dialogPane.getStylesheets().add(stylesheetPath);

        dialogPane.getStyleClass().add("dashboard-tile");
        dialogPane.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        setContentText(message);
        setHeaderText(header);
        setTitle(title);
        returnImage(this);

//        var cancel = ButtonType.CANCEL;
//        Button okButton = (Button) getDialogPane().lookupButton(cancel);
//        okButton.setId("btnClose");
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
