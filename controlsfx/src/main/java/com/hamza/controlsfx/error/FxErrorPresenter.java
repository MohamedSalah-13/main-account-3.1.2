package com.hamza.controlsfx.error;

import com.hamza.controlsfx.alert.AlertSetting;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import lombok.extern.log4j.Log4j2;

import java.util.Objects;

/** Presents an {@link ErrorReport} without exposing its original throwable. */
@Log4j2
public final class FxErrorPresenter {

    public void present(ErrorReport report) {
        Objects.requireNonNull(report, "report");
        if (Platform.isFxApplicationThread()) {
            showNow(report);
            return;
        }
        try {
            Platform.runLater(() -> showNow(report));
        } catch (IllegalStateException e) {
            log.error("JavaFX is not running; error report {} could not be shown",
                    report.referenceId(), e);
        }
    }

    private void showNow(ErrorReport report) {
        try {
            new AlertSetting(Alert.AlertType.ERROR,
                    report.message(), report.title(), report.title());
        } catch (RuntimeException e) {
            // Reporting an error must never replace it with a second UI failure.
            log.error("Could not present error report {}", report.referenceId(), e);
        }
    }
}
