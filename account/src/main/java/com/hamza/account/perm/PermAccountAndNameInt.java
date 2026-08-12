package com.hamza.account.perm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

public interface PermAccountAndNameInt {

    PermissionKey showAccounts();

    PermissionKey updateAccounts();

    PermissionKey deleteAccounts();

    PermissionKey showNames();

    PermissionKey createNames();

    PermissionKey updateNames();

    PermissionKey deleteNames();
}
