package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import com.hamza.controlsfx.view.PassCheckApplication;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCombination;

/**
 * Asks for a password and answers whether it was the right one.
 * <p>
 * The dialog it wraps is a bare {@link PassCheckApplication}: the theme, the
 * orientation, the button captions and the F10 accelerator are not part of it, so
 * whoever opens it has to fit them itself. That work sat inline in
 * {@code SettingButtons.deleteData()}, which meant the second screen to need a
 * password would have started by copying twelve lines of dialog plumbing to get
 * back to a single yes or no. It lives here now, next to {@link ChangePassView},
 * which does the same for the change-password dialog.
 * <p>
 * <b>Cancel is not a wrong password.</b> The dialog's result converter answers
 * {@code false} for CANCEL exactly as it does for a wrong entry, and the caller
 * announced "the password is incorrect" to a user who had pressed Cancel. Closing
 * the window with the X answered nothing at all and was silent. All three are one
 * thing here: the screen does not open, and only a real attempt that failed is
 * worth telling the user about.
 */
public final class PassCheckView {

    private PassCheckView() {
    }

    /**
     * @return {@code true} only if the password was entered and matched; a wrong
     * password reports itself, and cancelling reports nothing
     */
    public static boolean confirm(String expected) throws Exception {
        var check = new PassCheckApplication(expected);
        var dialogPane = check.getDialogPane();
        var scene = dialogPane.getScene();
        ThemeManager.apply(scene);
        ChangeOrientation.sceneOrientation(scene);

        Button buttonOK = (Button) dialogPane.lookupButton(ButtonType.OK);
        buttonOK.setDefaultButton(false);
        buttonOK.setText(Setting_Language.OK + " F10");
        Button buttonCancel = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        buttonCancel.setId("btnClose");
        buttonCancel.setText(Setting_Language.WORD_CANCEL);
        buttonCancel.setCancelButton(true);
        scene.getAccelerators().put(KeyCombination.keyCombination("F10"), buttonOK::fire);

        // The dialog answers false for CANCEL as well as for a wrong password, and
        // nothing at all for the X, so which of the three happened is remembered as
        // the button is pressed rather than guessed at from the answer.
        boolean[] attempted = {false};
        buttonOK.addEventFilter(ActionEvent.ACTION, event -> attempted[0] = true);

        boolean matched = check.showAndWait().orElse(false);
        if (!matched && attempted[0]) {
            AllAlerts.alertError(Setting_Language.THE_PASSWORD_IS_INCORRECT);
        }
        return matched;
    }
}
