package com.hamza.account.controller.main;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.controlsfx.button.api.ButtonMenuItemAction;

public interface ButtonWithPerm extends ButtonMenuItemAction {

    PermissionKey getPermissionType();
}
