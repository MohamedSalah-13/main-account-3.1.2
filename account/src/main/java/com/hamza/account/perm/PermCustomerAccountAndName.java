package com.hamza.account.perm;

import com.hamza.account.type.UserPermissionType;

public class PermCustomerAccountAndName implements PermAccountAndNameInt {
    @Override
    public UserPermissionType showAccounts() {
        return UserPermissionType.CUSTOMER_ACCOUNT_SHOW;
    }

    @Override
    public UserPermissionType updateAccounts() {
        return UserPermissionType.CUSTOMER_ACCOUNT_UPDATE;
    }

    @Override
    public UserPermissionType deleteAccounts() {
        return UserPermissionType.CUSTOMER_ACCOUNT_DELETE;
    }

    @Override
    public UserPermissionType showNames() {
        return UserPermissionType.CUSTOMER_SHOW;
    }

    @Override
    public UserPermissionType updateNames() {
        return UserPermissionType.CUSTOMER_UPDATE;
    }

    @Override
    public UserPermissionType deleteNames() {
        return UserPermissionType.CUSTOMER_DELETE;
    }
}
