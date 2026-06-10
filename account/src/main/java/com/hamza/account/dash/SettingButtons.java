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
import com.hamza.account.view.AboutApplication;
import com.hamza.account.view.OpenApplication;
import com.hamza.account.view.SettingApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class SettingButtons {

    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;

    public SettingButtons(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }


    public ButtonWithPerm setting() {
        return new ButtonWithPerm() {
            @Override
            public void action() throws Exception {
                new SettingApplication(daoFactory, dataPublisher).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_SETTING;
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
            public void action() {
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_MAIN;
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
            public void action() {
                System.exit(0);
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_CLOSE;
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
            public void action() throws Exception {
                new AboutApplication().start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.ABOUT;
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
            public void action() throws Exception {
                SaveDatabaseFile.saveBeforeClose(true);
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_BACKUP;
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
            public void action() throws Exception {
                new OpenApplication<>(new DeleteDataController(daoFactory, dataPublisher));
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_DELETE + " " + Setting_Language.DATA;
            }
        };
    }

    public ButtonWithPerm adminShifts() {
        return new ButtonWithPerm() {
            @Override
            public void action() throws Exception {
                var pane = new OpenFxmlApplication(new AdminShiftsController()).getPane();
                if (pane != null) {
                    Stage stage = new Stage();
                    stage.setTitle(textName());
                    stage.setScene(new Scene(pane));
                    stage.show();
                } else {
                    AllAlerts.alertError("Failed to load admin shifts pane");
                }
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADMIN;
            }
        };
    }
}
