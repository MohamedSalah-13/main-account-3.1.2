package com.hamza.account.controller.others;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.audit.AuditRetentionPolicy;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** Localized editor for a policy; preview and mutations remain asynchronous in the controller. */
final class AuditRetentionDialog {

    enum Action { SAVE, CLEAN_NOW }

    record Request(Action action, boolean enabled, int days, String reason) {
    }

    private AuditRetentionDialog() {
    }

    static Optional<Request> show(AuditRetentionPolicy policy) {
        Dialog<Request> dialog = new Dialog<>();
        dialog.setTitle(text("audit.log.retention.title"));
        dialog.setHeaderText(text("audit.log.retention.header"));
        ButtonType save = new ButtonType(text("audit.log.retention.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType clean = new ButtonType(text("audit.log.retention.clean"), ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(save, clean, ButtonType.CANCEL);

        CheckBox enabled = new CheckBox(text("audit.log.retention.enabled"));
        enabled.setSelected(policy.enabled());
        Spinner<Integer> days = new Spinner<>();
        days.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                AuditRetentionPolicy.MIN_DAYS, AuditRetentionPolicy.MAX_DAYS, policy.days(), 30));
        days.setEditable(true);
        TextArea reason = new TextArea();
        reason.setPromptText(text("audit.log.reason.prompt"));
        reason.setPrefRowCount(3);
        reason.setWrapText(true);

        Label hint = new Label(text("audit.log.retention.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("info-text");
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(6));
        form.add(enabled, 0, 0, 2, 1);
        form.add(new Label(text("audit.log.retention.days")), 0, 1);
        form.add(days, 1, 1);
        form.add(new Label(text("audit.log.reason.label")), 0, 2);
        form.add(reason, 1, 2);
        form.add(hint, 0, 3, 2, 1);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().setPrefWidth(540);
        dialog.getDialogPane().setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        ThemeManager.apply(dialog.getDialogPane().getScene());

        dialog.setResultConverter(button -> {
            if (button == save) return new Request(Action.SAVE, enabled.isSelected(), days.getValue(), reason.getText());
            if (button == clean) return new Request(Action.CLEAN_NOW, enabled.isSelected(), days.getValue(), reason.getText());
            return null;
        });
        return dialog.showAndWait();
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
