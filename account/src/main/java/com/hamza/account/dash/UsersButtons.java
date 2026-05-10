package com.hamza.account.dash;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.users.AddUserController;
import com.hamza.account.controller.users.UserController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.table.TableOpen;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.scene.Node;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class UsersButtons extends LoadData {


    public UsersButtons(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
    }

    public ButtonWithPerm getUsers_all() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new TableOpen<>(new UserController(daoFactory, dataPublisher, textName(), usersService)).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_USERS;
            }

        };
    }

    public ButtonWithPerm getUsers_add() {
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                return UserPermissionType.SETTING_SHOW;
            }

            @Override
            public void action() throws Exception {
                new AddForAllApplication(0, new AddUserController(0, dataPublisher.getPublisherAddUser()));
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD_USER;
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().setting);
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.ADD_USERS;
            }
        };
    }

}
