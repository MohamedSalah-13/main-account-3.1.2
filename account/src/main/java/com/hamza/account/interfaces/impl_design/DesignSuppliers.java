package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;

public class DesignSuppliers implements DesignInterface {

    @Override
    public String styleSheet() {
        return ThemeManager.getStylesheet();
    }

    @Override
    public String nameTextOfData() {
        return Setting_Language.WORD_SUP;
    }

    @Override
    public String nameTextOfAccount() {
        return Setting_Language.WORD_SUP_ACC;
    }

    @Override
    public String nameTextOfTotal() {
        return Setting_Language.TOTAL_PUR;
    }

    @Override
    public String nameTextOfInvoice() {
        return Setting_Language.WORD_PUR;
    }

    @Override
    public String nameTextOfReport() {
        return Setting_Language.WORD_REPORT_SUPP;
    }

    @Override
    public UserPermissionType show() {
        return UserPermissionType.PURCHASE_SHOW;
    }

    @Override
    public UserPermissionType update() {
        return UserPermissionType.PURCHASE_UPDATE;
    }

    @Override
    public UserPermissionType delete() {
        return UserPermissionType.PURCHASE_DELETE;
    }

    @Override
    public UserPermissionType show_totals() {
        return UserPermissionType.TOTAL_PURCHASE_SHOW;
    }

    @Override
    public UserPermissionType show_totals_invoice() {
        return UserPermissionType.TOTAL_PURCHASE_SHOW_INVOICE;
    }
}
