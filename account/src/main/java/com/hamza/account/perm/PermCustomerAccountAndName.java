package com.hamza.account.perm;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

public class PermCustomerAccountAndName implements PermAccountAndNameInt {
    @Override
    public PermissionKey showAccounts() {
        return AppPermissions.CUSTOMER_ACCOUNT_SHOW;
    }

    @Override
    public PermissionKey updateAccounts() {
        return AppPermissions.CUSTOMER_ACCOUNT_UPDATE;
    }

    @Override
    public PermissionKey deleteAccounts() {
        return AppPermissions.CUSTOMER_ACCOUNT_DELETE;
    }

    @Override
    public PermissionKey showNames() {
        return AppPermissions.CUSTOMER_SHOW;
    }

    @Override
    public PermissionKey createNames() {
        return AppPermissions.CUSTOMER_CREATE;
    }

    @Override
    public PermissionKey updateNames() {
        return AppPermissions.CUSTOMER_UPDATE;
    }

    @Override
    public PermissionKey deleteNames() {
        return AppPermissions.CUSTOMER_DELETE;
    }
}
