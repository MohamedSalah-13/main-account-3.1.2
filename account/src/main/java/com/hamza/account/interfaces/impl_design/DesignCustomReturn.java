package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.controlsfx.language.Setting_Language.TOTAL_SALES_RE;

public class DesignCustomReturn implements DesignInterface {

    @Override
    public String styleSheet() {
        return ThemeManager.getStylesheet();
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
        return TOTAL_SALES_RE;
    }

    @Override
    public String nameTextOfInvoice() {
        return Setting_Language.WORD_RE_SALES;
    }

    @Override
    public String nameTextOfReport() {
        return "تقارير مرتجع العملاء";
    }

    @Override
    public boolean showDataForCustomer() {
        return true;
    }

    @Override
    public UserPermissionType show() {
        return UserPermissionType.SALES_RE_SHOW;
    }

    @Override
    public UserPermissionType update() {
        return UserPermissionType.SALES_RE_UPDATE;
    }

    @Override
    public UserPermissionType delete() {
        return UserPermissionType.SALES_RE_DELETE;
    }

    @Override
    public UserPermissionType show_totals() {
        return UserPermissionType.TOTAL_SALES_RE_SHOW;
    }

    @Override
    public UserPermissionType show_totals_invoice() {
        return UserPermissionType.TOTAL_SALES_RE_SHOW_INVOICE;
    }


}
