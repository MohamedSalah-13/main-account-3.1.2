package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.Style_Sheet;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;

public class DesignCustom implements DesignInterface {

    @Override
    public String styleSheet() {
        var current = com.hamza.account.config.ThemeManager.getCurrentTheme();
        if (current == com.hamza.account.config.ThemeManager.Theme.DARK) {
            return Style_Sheet.getStyle();
        }
        return Style_Sheet.COLOR_SALES;
    }

    @Override
    public String nameTextOfData() {
        return Setting_Language.WORD_CUSTOM;
    }

    @Override
    public String nameTextOfAccount() {
        return Setting_Language.WORD_CUSTOM_ACC;
    }

    @Override
    public String nameTextOfTotal() {
        return Setting_Language.TOTAL_SALES;
    }

    @Override
    public String nameTextOfInvoice() {
        return Setting_Language.WORD_SALES;
    }

    @Override
    public String nameTextOfReport() {
        return Setting_Language.WORD_REPORT_CUSTOMER;
    }

    @Override
    public boolean showDataForCustomer() {
        return true;
    }

    @Override
    public boolean showScreenPaidInInvoice() {
        return true;
    }

    @Override
    public UserPermissionType show() {
        return UserPermissionType.SALES_SHOW;
    }

    @Override
    public UserPermissionType update() {
        return UserPermissionType.SALES_UPDATE;
    }

    @Override
    public UserPermissionType delete() {
        return UserPermissionType.SALES_DELETE;
    }

    @Override
    public UserPermissionType show_totals() {
        return UserPermissionType.TOTAL_SALES_SHOW;
    }

    @Override
    public UserPermissionType show_totals_invoice() {
        return UserPermissionType.TOTAL_SALES_SHOW_INVOICE;
    }

}
