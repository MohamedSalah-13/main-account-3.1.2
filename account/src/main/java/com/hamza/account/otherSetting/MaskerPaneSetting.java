package com.hamza.account.otherSetting;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.concurrent.Task;
import javafx.scene.effect.BlendMode;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.MaskerPane;

@Setter
@Getter
@Log4j2
public class MaskerPaneSetting extends MaskerPane {

    private Task<Void> voidTask;

    public MaskerPaneSetting(StackPane stackPane) {
        this.setVisible(false);
        this.setText(Setting_Language.PLEASE_WAIT);
        this.setBlendMode(BlendMode.EXCLUSION);
        stackPane.getChildren().add(this);
    }

    /**
     * Runs the work off the JavaFX thread with the "please wait" overlay up.
     * <p>
     * It used to do the opposite of that. The background thread's whole job was
     * {@code Platform.runLater(action)} followed by waiting on a latch, so the
     * backup, the wipe, the report and every query reached here ran <b>on the
     * JavaFX thread</b> while the worker sat blocked. Two things followed. The
     * window froze for the length of the operation - long enough on a wipe for
     * Windows to paint it as "not responding". And the overlay was made visible
     * inside that same {@code runLater}, so it could not be drawn until the work it
     * was announcing had already finished: what the user saw of it was the
     * {@code Thread.sleep(1000)} afterwards, which is also a second added to every
     * operation in the application for nothing.
     * <p>
     * <b>The action now runs on a worker thread</b>, so it must not touch the scene
     * graph or an {@code ObservableList} a table is showing. Read what it needs from
     * the controls before calling, and apply the result either through
     * {@code Platform.runLater} or in {@code getVoidTask().setOnSucceeded}, which
     * the callers that had a result to apply were already using.
     * <p>
     * Showing and hiding are hung off {@code runningProperty} rather than
     * {@code setOnSucceeded}, because callers set that themselves after calling this
     * and would replace anything left there.
     */
    public void showMaskerPane(MaskerPaneSetting.ActionMasherPane actionEvent) {
        showMaskerPane(null, actionEvent);
    }

    /** Runs a named operation and reports a failed task through the central policy. */
    public void showMaskerPane(String operation, MaskerPaneSetting.ActionMasherPane actionEvent) {
        voidTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                actionEvent.action();
                return null;
            }
        };
        AllAlerts.handleTaskFailure(operation, voidTask);
        // Task state is delivered on the JavaFX thread, and covers the failed and
        // cancelled endings as well as the successful one - the overlay cannot be
        // left over a screen whose work threw.
        voidTask.runningProperty().addListener((observable, was, running) -> setVisible(running));

        Thread thread = new Thread(voidTask, "masker-pane-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    public interface ActionMasherPane {
        void action() throws Exception;
    }
}
