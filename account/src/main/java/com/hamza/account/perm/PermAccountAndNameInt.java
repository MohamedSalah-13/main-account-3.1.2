package com.hamza.account.perm;

import com.hamza.account.type.UserPermissionType;

public interface PermAccountAndNameInt {

    UserPermissionType showAccounts();

    UserPermissionType updateAccounts();

    UserPermissionType deleteAccounts();

    UserPermissionType showNames();

    UserPermissionType updateNames();

    UserPermissionType deleteNames();
}
