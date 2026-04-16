package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.users.UserShiftController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.type.UserPermissionType;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.jetbrains.annotations.NotNull;

public class ShiftButtons extends LoadData {

    public ShiftButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    /**
     * Opens the shift management screen
     */
    public ButtonWithPerm openShiftScreen() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.UNITS_SHOW;
            }

            @Override
            public void action() throws Exception {
                // Action for non-tabpane scenario
            }

            @NotNull
            @Override
            public String textName() {
                return "إدارة الوردية";
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                var controller = new UserShiftController(daoFactory);
                var pane = new OpenFxmlApplication(controller).getPane();
                addTape(tabPane, pane, textName(), new Image_Setting().tools);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
            }
        };
    }
}
