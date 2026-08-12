package com.hamza.account.perm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

public class PermSuppliersAccountAndName implements PermAccountAndNameInt {
    @Override
    public PermissionKey showAccounts() {
        return AppPermissions.SUPPLIERS_ACCOUNT_SHOW;
    }

    @Override
    public PermissionKey updateAccounts() {
        return AppPermissions.SUPPLIERS_ACCOUNT_UPDATE;
    }

    @Override
    public PermissionKey deleteAccounts() {
        return AppPermissions.SUPPLIERS_ACCOUNT_DELETE;
    }

    @Override
    public PermissionKey showNames() {
        return AppPermissions.SUPPLIERS_SHOW;
    }

    @Override
    public PermissionKey createNames() {
        return AppPermissions.SUPPLIERS_CREATE;
    }

    @Override
    public PermissionKey updateNames() {
        return AppPermissions.SUPPLIERS_UPDATE;
    }

    @Override
    public PermissionKey deleteNames() {
        return AppPermissions.SUPPLIERS_DELETE;
    }
}
