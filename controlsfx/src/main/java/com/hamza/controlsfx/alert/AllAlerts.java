package com.hamza.controlsfx.alert;

import com.hamza.controlsfx.language.LanguageManager;
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

    private static final LanguageManager LANGUAGE_MANAGER = LanguageManager.getInstance();
    public static final String SAVE = LANGUAGE_MANAGER.getString("common.save");
    public static final String SAVE_ALL = LANGUAGE_MANAGER.getString("common.save.all");
    private static final String ERROR = LANGUAGE_MANAGER.getString("common.error");
    private static final String DELETE = LANGUAGE_MANAGER.getString("common.delete");
    private static final String DELETE_ALL = LANGUAGE_MANAGER.getString("common.delete.all");
    private static final String DELETE_DONE = LANGUAGE_MANAGER.getString("common.delete.done");
    private static final String MSG_DO_YOU_WANT_SAVE = LANGUAGE_MANAGER.getString("msg.do.you.want.save");
    private static final String MSG_DO_YOU_WANT_DELETE = LANGUAGE_MANAGER.getString("msg.delete.confirm");

    public static void alertError(String s) {
        new AlertSetting(Alert.AlertType.ERROR, s, ERROR, ERROR);
    }

    public static void alertSave() {
        alertSaveWithMessage(SAVE);
    }

    public static void alertSaveWithMessage(String message) {
        new AlertSetting(Alert.AlertType.INFORMATION,
                message, SAVE_ALL, SAVE);
    }

    public static void alertDelete() {
        alertDeleteWithMessage(DELETE_DONE);
    }

    public static void alertDeleteWithMessage(String message) {
        new AlertSetting(Alert.AlertType.INFORMATION,
                message, DELETE_ALL, DELETE);
    }

    public static boolean confirmDelete() {
        return confirm_all(DELETE, MSG_DO_YOU_WANT_DELETE);
    }

    public static boolean confirmSave() {
        return confirm_all(SAVE, MSG_DO_YOU_WANT_SAVE);
    }

    public static boolean confirm_all(String title, String message) {
        Alert alert = new AlertSetting(
                message, title, title);

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
