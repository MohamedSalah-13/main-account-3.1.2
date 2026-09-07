package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.features.users.SupportRecoveryChallenge;
import com.hamza.account.features.users.SupportRecoveryService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Local-only support recovery window, opened solely by the explicit command-line flag.
 *
 * <p>Two halves, in the order the operator works through them: the challenge this machine
 * has just issued, which they send to support; and the signed response that comes back,
 * which they paste in with the new password.
 *
 * <p>The response is pasted rather than read out. An RSA signature over this key is 384
 * bytes - more than five hundred characters of base64 - so dictating it was never a real
 * procedure; it arrives the way any other text does, and the challenge above has a button
 * to put it on the clipboard.
 */
public final class SupportRecoveryView {

    private SupportRecoveryView() { }

    public static void show(Stage owner, DaoFactory daoFactory) {
        LanguageManager language = LanguageManager.getInstance();
        SupportRecoveryService service = new SupportRecoveryService(daoFactory);

        SupportRecoveryChallenge challenge;
        try {
            challenge = service.issueChallenge();
        } catch (Exception error) {
            // Nothing can be recovered without one, so there is no window to open.
            AllAlerts.handleError(language.getString("support.recovery.title"), error);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(language.getString("support.recovery.title"));
        dialog.setHeaderText(language.getString("support.recovery.subtitle"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        TextArea challengeText = new TextArea(challenge.displayText());
        challengeText.setEditable(false);
        challengeText.setWrapText(true);
        challengeText.setPrefRowCount(2);
        Button copy = new Button(language.getString("support.recovery.copy"));
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(challenge.displayText());
            Clipboard.getSystemClipboard().setContent(content);
        });

        TextArea response = new TextArea();
        response.setWrapText(true);
        response.setPrefRowCount(4);
        response.setPromptText(language.getString("support.recovery.response.hint"));

        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label(language.getString("support.recovery.challenge")), challengeText);
        form.addRow(1, new Label(""), new HBox(copy));
        form.addRow(2, new Label(language.getString("support.recovery.response")), response);
        form.addRow(3, new Label(language.getString("support.recovery.password")), password);
        form.addRow(4, new Label(language.getString("user.password.confirm")), confirmation);
        Label expiry = new Label(language.getString("support.recovery.expiry",
                SupportRecoveryChallenge.VALID_FOR_MINUTES));
        expiry.setWrapText(true);
        form.addRow(5, new Label(""), expiry);
        dialog.getDialogPane().setContent(form);
        ThemeManager.apply(dialog.getDialogPane().getScene());

        // On a button press the result converter runs and the dialog closes whatever it
        // returns, so reporting an error from there sent the operator back to a command
        // line to start again - and with a challenge that is spent for nothing. The filter
        // is the way to refuse the close; it is what DialogApplication uses.
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (!password.getText().equals(confirmation.getText())) {
                    throw new UserValidationException(language.getString("support.recovery.error.confirm"));
                }
                service.redeem(response.getText(), password.getText());
            } catch (Exception error) {
                AllAlerts.handleError(language.getString("support.recovery.title"), error);
                event.consume();
            }
        });
        dialog.showAndWait();
    }
}
