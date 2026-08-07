package com.hamza.controlsfx.alert;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

@Log4j2
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
        showOnFxThread(() -> new AlertSetting(Alert.AlertType.ERROR, s, ERROR, ERROR));
    }

    public static void alertSave() {
        alertSaveWithMessage(SAVE);
    }

    public static void alertSaveWithMessage(String message) {
        showOnFxThread(() -> new AlertSetting(Alert.AlertType.INFORMATION,
                message, SAVE_ALL, SAVE));
    }

    public static void alertDelete() {
        alertDeleteWithMessage(DELETE_DONE);
    }

    public static void alertDeleteWithMessage(String message) {
        showOnFxThread(() -> new AlertSetting(Alert.AlertType.INFORMATION,
                message, DELETE_ALL, DELETE));
    }

    public static boolean confirmDelete() {
        return confirm_all(DELETE, MSG_DO_YOU_WANT_DELETE);
    }

    public static boolean confirmSave() {
        return confirm_all(SAVE, MSG_DO_YOU_WANT_SAVE);
    }

    public static boolean confirm_all(String title, String message) {
        return askOnFxThread(() -> {
            Alert alert = new AlertSetting(message, title, title);

            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        });
    }

    public static void showExceptionDialog(Throwable throwable) {
        showOnFxThread(() -> {
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
        });
    }

    /**
     * Shows a dialog on the JavaFX application thread.
     * <p>
     * A JavaFX dialog may only be built and shown on that thread, and these are
     * reached from background threads all over the app - the invoice save thread,
     * the low-stock notification, the backup scheduler. Called from there, even
     * constructing the Alert threw {@code IllegalStateException}, which replaced
     * whatever error was being reported with an unrelated one.
     * <p>
     * On the FX thread the dialog is shown inline and blocks, exactly as before.
     * From any other thread it is posted to the FX thread and the caller carries
     * on, rather than parking a background thread on a dialog nothing is waiting
     * for.
     */
    private static void showOnFxThread(Runnable dialog) {
        if (Platform.isFxApplicationThread()) {
            dialog.run();
            return;
        }
        try {
            Platform.runLater(dialog);
        } catch (IllegalStateException e) {
            log.error("JavaFX is not running, so this alert could not be shown", e);
        }
    }

    /**
     * As {@link #showOnFxThread}, but for a dialog whose answer the caller needs.
     * An answer cannot be produced without waiting for one, so a background caller
     * does block here until the user replies.
     * <p>
     * If the dialog cannot be shown at all the answer is {@code false}. Both
     * callers read that as "the user did not agree", which leaves the save or the
     * delete undone - the safe direction to fail in.
     */
    private static boolean askOnFxThread(Supplier<Boolean> dialog) {
        if (Platform.isFxApplicationThread()) {
            return dialog.get();
        }

        FutureTask<Boolean> task = new FutureTask<>(dialog::get);
        try {
            Platform.runLater(task);
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for the user to answer", e);
            return false;
        } catch (ExecutionException | IllegalStateException e) {
            log.error("Could not ask the user for confirmation", e);
            return false;
        }
    }
}
