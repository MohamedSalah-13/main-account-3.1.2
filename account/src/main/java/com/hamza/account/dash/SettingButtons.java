package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.DeleteDataController;
import com.hamza.account.controller.users.AdminShiftsController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.view.AboutApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.account.view.PassCheckView;
import com.hamza.account.view.SettingApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;


@Log4j2
public class SettingButtons {

    /**
     * The second lock on the "delete data" screen, over and above the permission.
     * <p>
     * It is not the signed-in user's password on purpose: the point of it is that
     * the person sitting at the till, permission or not, cannot empty the database
     * without whoever installed the system. It is also not a secret - it is a
     * literal in a jar anyone can read with {@code strings} - so treat it as a
     * "are you sure you are the right person" gate and not as security.
     */
    private static final String WIPE_PASSWORD = "147852369";

    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;

    public SettingButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }


    public ButtonWithPerm setting() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new SettingApplication(daoFactory, dataPublisher).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("menu.settings");
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().setting);
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.SETTING;
            }
        };
    }

    public ButtonWithPerm home() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("main");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.HOME;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) {
                tabPane.getSelectionModel().selectFirst();
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }
        };
    }

    public ButtonWithPerm close() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.USER_SHIFT_MANAGE;
            }

            @Override
            public void action() {
                System.exit(0);
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("common.close");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

        };
    }

    public ButtonWithPerm about() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new AboutApplication().start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.about");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }
        };
    }

    public ButtonWithPerm backup() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                SaveDatabaseFile.saveBeforeClose(true);
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("backup");
            }

            @Override
            public boolean disableBoolean() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.KEY_BACKUP;
            }
        };
    }

    public ButtonWithPerm deleteData() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                if (PassCheckView.confirm(WIPE_PASSWORD)) {
                    new OpenApplication<>(new DeleteDataController());
                }
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("nav.delete.data");
            }
        };
    }

    public ButtonWithPerm adminShifts() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.PUBLIC_ACCESS;
            }

            @Override
            public void action() throws Exception {
                var pane = new OpenFxmlApplication(new AdminShiftsController()).getPane();
                if (pane != null) {
                    Stage stage = new Stage();
                    stage.setTitle(textName());
                    stage.setScene(new Scene(pane));
                    stage.show();
                } else {
                    AllAlerts.reportError(LanguageManager.getInstance().getString("user.shift.error.open.screen.title"),
                            new IllegalStateException("Failed to load admin shifts pane"));
                }
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("user.shift.admin.button");
            }
        };
    }
}
