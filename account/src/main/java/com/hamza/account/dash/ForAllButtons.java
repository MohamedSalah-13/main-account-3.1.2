package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.features.choiceDialoge.ChangeUserName;
import com.hamza.account.model.dao.DaoFactory;
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


    public ForAllButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm calc() {
        return new ButtonWithPerm() {

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


    public ButtonWithPerm changePassword() {
        return new ButtonWithPerm() {
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


    public ButtonWithPerm changeName() {
        return new ButtonWithPerm() {

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

    public ButtonWithPerm alarm() {
        return new ButtonWithPerm() {

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

    public ButtonWithPerm logout() {
        return new ButtonWithPerm() {

            @Override
            public void action() throws Exception {
                if (AllAlerts.confirm_all("logout", "هل تريد الخروج")) {
                    dataPublisher.getCloseStageFromLogout().setAvailability(true);
                    new LogApplication(daoFactory).start(new Stage());
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
