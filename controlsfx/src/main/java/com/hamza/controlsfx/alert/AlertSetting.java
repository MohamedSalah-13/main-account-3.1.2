package com.hamza.controlsfx.alert;

import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ImageSetting;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AlertSetting extends Alert {

    private static final List<String> stylesheetPaths = new ArrayList<>();

    /**
     * Points kept free at the end of every line {@link MessageLines} builds, so the label never
     * finds one a rounding step too wide and splits it itself - which on Linux is the defect the
     * lines are built to avoid.
     */
    private static final double LINE_MARGIN = 2;

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

        LanguageManager languageManager = LanguageManager.getInstance();
        dialogPane.setNodeOrientation(languageManager.getNodeOrientation());
        setContentText(message);
        breakContentIntoLines(dialogPane, message);
        setHeaderText(header);
        setTitle(title);
        returnImage(this);

        buttonOk(this, ButtonType.OK, languageManager.getString("ok"));
        if (b) buttonOk(this, ButtonType.CANCEL, languageManager.getString("cancel"));

        Toolkit.getDefaultToolkit().beep();
    }

    /**
     * Rewrites the message with a line break wherever the content label would wrap it, measured in
     * the label's own font against the width the dialog lays it out at. See {@link MessageLines}
     * for why the label is not left to wrap it.
     */
    private void breakContentIntoLines(DialogPane dialogPane, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        dialogPane.applyCss();
        Label content = dialogPane.getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof Label && node.getStyleClass().contains("content"))
                .map(Label.class::cast)
                .findFirst()
                .orElse(null);
        if (content == null || !(content.getPrefWidth() > 0)) {
            return;
        }
        double width = content.getPrefWidth() - content.snappedLeftInset() - content.snappedRightInset()
                - LINE_MARGIN;
        Text measure = new Text();
        measure.setFont(content.getFont());
        setContentText(MessageLines.wrap(message, width, line -> {
            measure.setText(line);
            return measure.getLayoutBounds().getWidth();
        }));
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