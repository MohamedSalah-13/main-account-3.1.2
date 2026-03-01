package com.hamza.controlsfx.alert;

import com.hamza.controlsfx.language.StringConstants;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

public class AllAlerts {

    public static void alertError(String s) {
        new AlertSetting(Alert.AlertType.ERROR, s, StringConstants.WRONG, StringConstants.WRONG);
    }

    public static void alertSave() {
        alertSaveWithMessage(StringConstants.SAVE_DONE);
    }

    public static void alertSaveWithMessage(String message) {
        new AlertSetting(Alert.AlertType.INFORMATION,
                message,
                StringConstants.SAVE_ALL_DATA,
                StringConstants.SAVE);
    }

    public static void alertDelete() {
        alertDeleteWithMessage(StringConstants.DELETE_DONE);
    }

    public static void alertDeleteWithMessage(String message) {
        new AlertSetting(Alert.AlertType.INFORMATION,
                message,
                StringConstants.DELETE_ALL_DATA,
                StringConstants.DELETE_DATA);
    }

    public static boolean confirmDelete() {
        return confirm_all(StringConstants.DELETE);
    }

    public static boolean confirmSave() {
        return confirm_all(StringConstants.SAVE);
    }

    public static boolean confirm_all(String s) {
        Alert alert = new AlertSetting(
                StringConstants.DO_YOU_WANT + " " + s,
                s + " " + StringConstants.ALL_DATA,
                s + " " + StringConstants.DATA);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showExceptionDialog(Throwable throwable) {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(throwable.getMessage());
        alert.setHeaderText(throwable.getMessage());
        alert.setContentText(throwable.getMessage());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        String exceptionText = sw.toString();

        Label label = new Label("The exception stacktrace was:");

        TextArea textArea = new TextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(expContent);
        Toolkit.getDefaultToolkit().beep();
        alert.showAndWait();
    }
}
