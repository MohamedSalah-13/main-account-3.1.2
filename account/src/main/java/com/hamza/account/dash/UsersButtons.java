package com.hamza.account.dash;

import com.hamza.account.controller.main.ButtonWithPerm;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.users.AddUserController;
import com.hamza.account.controller.users.UserController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.otherSetting.KeyCodeCombinationSetting;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.account.view.OpenApplication;
import javafx.scene.input.KeyCodeCombination;
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
            public PermissionKey getPermissionType() {
                return AppPermissions.USERS_SHOW;
            }

            @Override
            public void action() throws Exception {
                new OpenApplication<>(new UserController());
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("users");
            }

        };
    }

    public ButtonWithPerm getUsers_add() {
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return AppPermissions.USERS_MANAGE;
            }

            @Override
            public void action() throws Exception {
                new AddForAllApplication(0, new AddUserController(0));
            }

            @NotNull
            @Override
            public String textName() {
                return LanguageManager.getInstance().getString("addUser");
            }

            @Override
            public KeyCodeCombination acceleratorKey() {
                return KeyCodeCombinationSetting.ADD_USERS;
            }
        };
    }

}
