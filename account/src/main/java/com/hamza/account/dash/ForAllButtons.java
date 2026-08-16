package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.features.choiceDialoge.ChangeUserName;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.ChangePassView;
import com.hamza.account.view.ApplicationNavigator;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import static com.hamza.account.otherSetting.KeyCodeCombinationSetting.*;

public class ForAllButtons extends LoadData {


    public ForAllButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm calc() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.PUBLIC_ACCESS;
            }

            @Override
            public void action() throws Exception {
                new ProcessBuilder("calc.exe").start();
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.calculator");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                new ProcessBuilder("calc.exe").start();
            }
        };
    }

    /**
     * Creates a new ButtonWithPerm for changing the user password.
     * This method sets up the action to open the password change interface
     * in a new stage, disables the menu item button by default, assigns
     * a specific text label for the menu item, and sets up an accelerator
     * key combination for quick activation.
     *
     * @return an instance of ButtonWithPerm configured for the password change functionality.
     */
    public ButtonWithPerm changePassword() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_UPDATE_PASS;
            }

            @Override
            public void action() throws Exception {
                new ChangePassView(daoFactory);
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.change.password");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return CHANGE_PASS;
            }
        };
    }

    /**
     * Creates and returns a ButtonWithPerm implementation to handle the change name functionality.
     * This method sets up a dialog to allow the user to change their name, validates the input,
     * updates the user object in the data storage, and provides feedback regarding the operation's success or failure.
     *
     * @return a ButtonWithPerm that defines the change name operation including its specific behaviors and properties
     */
    public ButtonWithPerm changeName() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_UPDATE_NAME;
            }

            @Override
            public void action() {
                new ChangeUserName(textName(), daoFactory, dataPublisher);
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.change.name");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return CHANGE_NAME;
            }
        };
    }

    /**
     * Provides a logout action for the application, encapsulated as a button menu item action.
     * When triggered, this action:
     * - Publishes true on the logout publisher, closing the open stages.
     * - Initializes and starts the {@code LogApplication} for user re-login.
     *
     * @return a {@code ButtonWithPerm} instance that handles the logout process,
     * including publishing the logout event, providing the menu label,
     * and enabling a specific keyboard shortcut for logout.
     */
    public ButtonWithPerm logout() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.PUBLIC_ACCESS;
            }

            @Override
            public void action() throws Exception {
                LanguageManager language = LanguageManager.getInstance();
                if (AllAlerts.confirm_all(language.getString("session.logout.title"),
                        language.getString("session.logout.confirm"))) {
                    ApplicationNavigator navigator = ServiceRegistry.get(ApplicationNavigator.class);
                    if (navigator == null) throw new IllegalStateException("Application navigator is not registered");
                    navigator.logout();
                }
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("logout");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return LOGOUT;
            }
        };
    }
}
