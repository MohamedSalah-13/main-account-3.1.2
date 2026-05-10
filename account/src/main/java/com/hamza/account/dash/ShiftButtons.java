package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.users.UserShiftController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.type.UserPermissionType;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
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
                var controller = new UserShiftController();
                Scene scene = new Scene(new OpenFxmlApplication(controller).getPane()
                        , 300, 300);
                Stage stage = new Stage();
                stage.setScene(scene);
                stage.show();
            }

            @NotNull
            @Override
            public String textName() {
                return "إدارة الوردية";
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
            }
        };
    }
}
