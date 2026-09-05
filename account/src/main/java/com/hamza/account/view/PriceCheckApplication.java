package com.hamza.account.view;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.pricecheck.PriceCheckController;
import com.hamza.account.controller.pricecheck.PriceCheckSetupDialog;
import com.hamza.account.features.pricecheck.PriceCheckService;
import com.hamza.account.features.pricecheck.PriceCheckSettings;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.service.CardItemService;
import com.hamza.account.service.ItemsService;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.notifications.NotificationToaster;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Opens the price-check screen as a locked full-screen window.
 *
 * <p>The lock is the point of the class. This screen is left running on a device hanging
 * in the shop, where anyone can reach the keyboard, and behind it sits an application that
 * can write invoices and read the day's takings. So:
 *
 * <ul>
 *   <li>{@code setFullScreenExitKeyCombination(NO_MATCH)} - without it Escape leaves full
 *       screen and the till is on display, which is the whole protection gone by accident;</li>
 *   <li>closing needs {@code Ctrl+Shift+Q} <b>and</b> the signed-in user's own password.
 *       A permission check alone would protect nothing: the person at the keyboard is a
 *       customer, not the user whose session is running.</li>
 * </ul>
 *
 * <p>Which warehouse and which price tier it answers for is asked once, before the window
 * opens - {@link PriceCheckSetupDialog}. A balance is per warehouse, so a screen that
 * guessed would report another branch's stock.
 */
public class PriceCheckApplication extends Application {

    private static final KeyCombination EXIT_KEY =
            new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);

    /**
     * What leaving the screen means. Null for the window opened from the sidebar, which is
     * a second window and simply closes. A kiosk account signs in <i>onto</i> this screen
     * instead, so leaving it there is the end of a session and hands back to the login
     * screen - not an exit, since a device on a wall has nobody to start it again.
     */
    private final Runnable onExit;

    public PriceCheckApplication() {
        this(null);
    }

    public PriceCheckApplication(Runnable onExit) {
        this.onExit = onExit;
    }

    @Override
    public void start(Stage stage) throws Exception {
        PriceCheckService.requireAccess();

        Optional<PriceCheckSettings> chosen = settings();
        if (chosen.isEmpty()) {
            // Cancelled setup. From the sidebar that is simply "never mind"; on a kiosk
            // account there is nothing else this session could show, so it ends.
            leave(stage);
            return;
        }

        var service = new PriceCheckService(
                ServiceRegistry.get(ItemsService.class),
                ServiceRegistry.get(CardItemService.class));
        var controller = new PriceCheckController(service, chosen.get(),
                PropertiesName.getPriceCheckResetSeconds());

        Scene scene = new SceneAll(new OpenFxmlApplication(controller).getPane());
        scene.getAccelerators().put(EXIT_KEY, () -> closeIfAllowed(stage));

        stage.setScene(scene);
        stage.setTitle(LanguageManager.getInstance().getString("pricecheck.title"));
        // No toast over a screen a customer is reading. The first run of this screen had
        // "709 items are running low" announced in its corner - the shop's stock position,
        // in front of whoever was standing there. The inbox behind the bell still has it.
        stage.getProperties().put(NotificationToaster.SUPPRESS_TOASTS, true);
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        stage.setFullScreen(true);
        // The X and Alt+F4 go through the same gate as the exit key; a window that can be
        // closed by the taskbar is not locked.
        stage.setOnCloseRequest(event -> {
            event.consume();
            closeIfAllowed(stage);
        });
        stage.show();
    }

    /**
     * The setup to open with: what this device already carries when a kiosk account signed
     * in, and the dialog otherwise. The sidebar always asks - someone is standing there.
     */
    private Optional<PriceCheckSettings> settings() throws Exception {
        if (onExit != null) {
            Optional<PriceCheckSettings> remembered = PriceCheckSetupDialog.remembered();
            if (remembered.isPresent()) {
                return remembered;
            }
        }
        return new PriceCheckSetupDialog().ask();
    }

    private void closeIfAllowed(Stage stage) {
        if (confirmWithCurrentUserPassword(stage)) {
            leave(stage);
        }
    }

    private void leave(Stage stage) {
        stage.setFullScreen(false);
        if (onExit != null) {
            onExit.run();
        } else {
            stage.close();
        }
    }

    /**
     * @return true only when the entered password is the signed-in user's own. Cancelling
     * is not a wrong password and says nothing - the same distinction {@link PassCheckView}
     * draws, for the same reason.
     */
    private boolean confirmWithCurrentUserPassword(Stage stage) {
        Users user = CurrentUser.getOrNull();
        if (user == null || user.getPasswordHash() == null) {
            return false;
        }
        var lm = LanguageManager.getInstance();

        PasswordField password = new PasswordField();
        Dialog<String> dialog = new Dialog<>();
        // Owned by the full-screen window, or it opens behind it: the screen would look
        // frozen with the only way out invisible on another layer.
        dialog.initOwner(stage);
        dialog.setTitle(lm.getString("pricecheck.exit.title"));
        dialog.getDialogPane().setHeaderText(lm.getString("pricecheck.exit.header"));
        dialog.getDialogPane().setContent(new VBox(10,
                new Label(lm.getString("pricecheck.exit.prompt")), password));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
        // The caret has been held in the scan field all day; the dialog has to claim it back
        // or there is nowhere visible to type the password.
        dialog.setOnShown(shown -> Platform.runLater(password::requestFocus));

        String entered = dialog.showAndWait().orElse(null);
        return entered != null && PasswordHasher.matches(entered, user.getPasswordHash()).matched();
    }
}
