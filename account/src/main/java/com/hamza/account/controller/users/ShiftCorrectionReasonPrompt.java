package com.hamza.account.controller.users;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.DialogButtons;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.shift.ShiftMode;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

/** Collects the audit reason only when shift tracking is enabled for this business. */
public final class ShiftCorrectionReasonPrompt {
    private ShiftCorrectionReasonPrompt() { }

    public static Optional<String> forUpdate() throws DaoException {
        return request("user.shift.correction.update.header");
    }

    public static Optional<String> forDelete() throws DaoException {
        return request("user.shift.correction.delete.header");
    }

    private static Optional<String> request(String headerKey) throws DaoException {
        ShiftPolicyService policies = ServiceRegistry.get(ShiftPolicyService.class);
        if (policies == null || policies.current().mode() == ShiftMode.DISABLED) {
            return Optional.of("");
        }

        LanguageManager language = LanguageManager.getInstance();
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(language.getString("user.shift.correction.title"));
        dialog.setHeaderText(language.getString(headerKey));
        dialog.setContentText(language.getString("user.shift.correction.prompt"));
        dialog.getEditor().setPromptText(language.getString("user.shift.correction.example"));
        dialog.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().length() <= 500 ? change : null));

        DialogButtons.changeNameAndGraphic(dialog.getDialogPane());
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(Bindings.createBooleanBinding(
                () -> dialog.getEditor().getText() == null || dialog.getEditor().getText().isBlank(),
                dialog.getEditor().textProperty()));
        ChangeOrientation.sceneOrientation(dialog.getDialogPane().getScene());
        ThemeManager.apply(dialog.getDialogPane().getScene());
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty());
    }
}
