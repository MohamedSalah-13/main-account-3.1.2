package com.hamza.account.perm;

import com.hamza.account.type.UserPermissionType;

public class PermSuppliersAccountAndName implements PermAccountAndNameInt {
    @Override
    public UserPermissionType showAccounts() {
        return UserPermissionType.SUPPLIERS_ACCOUNT_SHOW;
    }

    @Override
    public UserPermissionType updateAccounts() {
        return UserPermissionType.SUPPLIERS_ACCOUNT_UPDATE;
    }

    @Override
    public UserPermissionType deleteAccounts() {
        return UserPermissionType.SUPPLIERS_ACCOUNT_DELETE;
    }

    @Override
    public UserPermissionType showNames() {
        return UserPermissionType.SUPPLIERS_SHOW;
    }

    @Override
    public UserPermissionType updateNames() {
        return UserPermissionType.SUPPLIERS_UPDATE;
    }

    @Override
    public UserPermissionType deleteNames() {
        return UserPermissionType.SUPPLIERS_DELETE;
    }
}
