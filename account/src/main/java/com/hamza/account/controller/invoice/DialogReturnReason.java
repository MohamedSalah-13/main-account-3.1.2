package com.hamza.account.controller.invoice;

import com.hamza.account.features.returns.ReturnReason;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;

/**
 * Asks why goods came back on a return that names no invoice.
 * <p>
 * The sourced half of this question is the combo inside {@link DialogReturnFromInvoice}, which a
 * free return never opens - so until this existed a free return could not be given a reason at
 * all, and the reasons report filed every one of them under "none given" whatever the person
 * would have said.
 * <p>
 * A {@code ChoiceDialog} rather than a control on the invoice form: the question is asked once,
 * at the moment of saving, and a permanently visible combo that is meaningless as soon as an
 * invoice is picked would have to be shown and hidden with the rest of the return-only controls.
 */
final class DialogReturnReason {

    private DialogReturnReason() {
    }

    /**
     * @param current what the return already carries, so re-saving one does not start blank
     * @return the chosen reason, or {@code null} for "not given" - cancelling is that answer and
     *         never cancels the save, which is the caller's own separate confirmation
     */
    static ReturnReason ask(ReturnReason current) {
        var lang = LanguageManager.getInstance();
        List<ReturnReason> reasons = List.of(ReturnReason.values());
        ChoiceDialog<ReturnReason> dialog = new ChoiceDialog<>(
                current != null && reasons.contains(current) ? current : null, reasons);
        dialog.setTitle(lang.getString("return.dialog.reason.label"));
        dialog.setHeaderText(null);
        dialog.setContentText(lang.getString("return.dialog.reason.prompt"));
        dialog.getDialogPane().lookupButton(ButtonType.CANCEL)
                .setAccessibleText(lang.getString("return.reason.none"));
        @SuppressWarnings("unchecked")
        var combo = (javafx.scene.control.ComboBox<ReturnReason>) dialog.getDialogPane()
                .lookup(".combo-box");
        if (combo != null) {
            combo.setConverter(converter());
        }
        Optional<ReturnReason> chosen = dialog.showAndWait();
        return chosen.orElse(null);
    }

    private static StringConverter<ReturnReason> converter() {
        return new StringConverter<>() {
            @Override
            public String toString(ReturnReason reason) {
                return reason == null ? "" : reason.label();
            }

            @Override
            public ReturnReason fromString(String text) {
                return null;
            }
        };
    }
}
