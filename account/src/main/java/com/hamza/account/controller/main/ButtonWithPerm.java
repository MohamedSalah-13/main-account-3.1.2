package com.hamza.account.controller.main;

import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.button.api.ButtonMenuItemAction;

public interface ButtonWithPerm extends ButtonMenuItemAction {

    UserPermissionType getPermissionType();
}
