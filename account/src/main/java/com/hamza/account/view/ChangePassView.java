package com.hamza.account.view;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.users.ChangePassController;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.features.users.JdbcPasswordChangeRepository;
import com.hamza.account.features.users.PasswordChangeService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.util.Objects;

/** Opens the account-owned password dialog for optional and forced first-login changes. */
public final class ChangePassView {

    public ChangePassView(DaoFactory daoFactory) throws Exception {
        this(daoFactory, false, () -> { }, () -> { });
    }

    /** Used after a bootstrap login: cancelling leaves the user at the login screen. */
    public ChangePassView(DaoFactory daoFactory, Runnable onPasswordChanged, Runnable onCancelled)
            throws Exception {
        this(daoFactory, true, onPasswordChanged, onCancelled);
    }

    private ChangePassView(DaoFactory daoFactory, boolean forced, Runnable onPasswordChanged,
                           Runnable onCancelled) throws Exception {
        Objects.requireNonNull(daoFactory, "daoFactory");
        Objects.requireNonNull(onPasswordChanged, "onPasswordChanged");
        Objects.requireNonNull(onCancelled, "onCancelled");

        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        if (session == null || !session.isSignedIn()) {
            throw new IllegalStateException("A signed-in user is required to change a password");
        }
        Users currentUser = CurrentUser.get();
        PasswordChangeService service = new PasswordChangeService(
                new JdbcPasswordChangeRepository(daoFactory), session);
        ChangePassController controller = new ChangePassController(service, session,
                currentUser.getId(), currentUser.getUsername(), forced);

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("change-password.fxml"),
                LanguageManager.getInstance().getResourceBundle());
        loader.setControllerFactory(type -> controller);

        Dialog<Boolean> dialog = new Dialog<>();
        DialogPane dialogPane = new DialogPane();
        dialogPane.getStyleClass().add("password-change-dialog");
        dialogPane.getStylesheets().add(Objects.requireNonNull(ChangePassView.class.getResource(
                "/com/hamza/account/css/password-change.css")).toExternalForm());
        dialogPane.setContent(loader.load());
        // JavaFX refuses title-bar/Alt+F4 closes when a DialogPane has no cancel
        // ButtonType. The visible actions live in the modern content, so retain one
        // unmanaged native cancel button solely to preserve that platform contract.
        dialogPane.getButtonTypes().add(ButtonType.CANCEL);
        Button nativeCancel = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
        nativeCancel.setVisible(false);
        nativeCancel.setManaged(false);
        dialog.setDialogPane(dialogPane);
        dialog.setTitle(text(forced ? "password.change.required.title" : "password.change.title"));
        dialog.setResizable(true);
        dialog.setResultConverter(buttonType -> Boolean.FALSE);
        controller.attach(dialog);
        dialog.setOnShown(event -> controller.requestInitialFocus());
        dialog.setOnCloseRequest(event -> {
            if (controller.isBusy()) {
                event.consume();
            } else if (!Boolean.TRUE.equals(dialog.getResult())) {
                dialog.setResult(false);
            }
        });

        ThemeManager.apply(dialogPane.getScene());
        ChangeOrientation.sceneOrientation(dialogPane.getScene());

        boolean changed = dialog.showAndWait().orElse(false);
        controller.dispose();
        if (changed) {
            AllAlerts.alertSaveWithMessage(text("password.change.success"));
            onPasswordChanged.run();
        } else {
            onCancelled.run();
        }
    }

    private static String text(String key) {
        return LanguageManager.getInstance().getString(key);
    }
}
