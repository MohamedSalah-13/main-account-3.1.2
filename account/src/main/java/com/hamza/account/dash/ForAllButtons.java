package com.hamza.account.dash;

import com.hamza.account.features.choiceDialoge.ChangeUserName;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.ChangePassView;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import static com.hamza.account.otherSetting.KeyCodeCombinationSetting.*;

public class ForAllButtons extends LoadData {

    private final LoadDataAndList loadDataAndList;

    public ForAllButtons(DaoFactory daoFactory, DataPublisher dataPublisher, LoadDataAndList loadDataAndList) throws Exception {
        super(daoFactory, dataPublisher);
        this.loadDataAndList = loadDataAndList;
    }

    /**
     * Creates and returns a ButtonWithPerm that launches the calculator application.
     *
     * @return a ButtonWithPerm which executes the calculator application when its action is triggered.
     */
    public ButtonWithPerm calc() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return null;
            }

            @Override
            public void action() throws Exception {
                Runtime.getRuntime().exec("Calc");
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.CALC;
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                Runtime.getRuntime().exec("Calc");
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
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SETTING_UPDATE_PASS;
            }

            @Override
            public void action() throws Exception {
                new ChangePassView(daoFactory);
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.CHANGE_PASS;
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
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SETTING_UPDATE_NAME;
            }

            @Override
            public void action() {
                new ChangeUserName(textName(), daoFactory, dataPublisher);
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.CHANGE_NAME;
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
     * Creates and returns a ButtonWithPerm that represents the "Alarm" action.
     * The returned action includes functionality for responding to specific button actions
     * as well as providing the associated text name.
     *
     * @return a ButtonWithPerm instance configured for the "Alarm" action.
     */
    public ButtonWithPerm alarm() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return null;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.Alerts;
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }
        };
    }

    /**
     * Provides a logout action for the application, encapsulated as a button menu item action.
     * When triggered, this action:
     * - Sets the availability status of the logout stage to true.
     * - Initializes and starts the {@code LogApplication} for user re-login.
     *
     * @return a {@code ButtonWithPerm} instance that handles the logout process,
     * including setting the logout stage availability, providing the menu label,
     * and enabling a specific keyboard shortcut for logout.
     */
    public ButtonWithPerm logout() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return null;
            }

            @Override
            public void action() throws Exception {
                if (AllAlerts.confirm_all("logout","هل تريد الخروج")) {
                    dataPublisher.getCloseStageFromLogout().setAvailability(true);
                    new LogApplication(daoFactory, loadDataAndList).start(new Stage());
                }
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_LOGOUT;
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
