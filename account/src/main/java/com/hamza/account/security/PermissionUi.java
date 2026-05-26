package com.hamza.account.security;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.service.permission.AuthorizationService;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.database.DaoException;
import javafx.scene.control.Control;

public class PermissionUi {

    private static final AuthorizationService authorizationService =
            ServiceRegistry.get(AuthorizationService.class);

    public static void disableIfNotAllowed(Control control, PermissionCode permissionCode) {
        try {
            int userId = LogApplication.usersVo.getId();
            boolean allowed = authorizationService.hasPermission(userId, permissionCode);
            control.setDisable(!allowed);
        } catch (DaoException e) {
            control.setDisable(true);
        }
    }

    public static boolean has(PermissionCode permissionCode) {
        try {
            int userId = LogApplication.usersVo.getId();
            return authorizationService.hasPermission(userId, permissionCode);
        } catch (DaoException e) {
            return false;
        }
    }
}
