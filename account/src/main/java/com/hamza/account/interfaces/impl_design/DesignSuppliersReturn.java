package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.Style_Sheet;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.controlsfx.language.Setting_Language.TOTAL_PUR_RE;

public class DesignSuppliersReturn implements DesignInterface {

    @Override
    public String styleSheet() {
        var current = com.hamza.account.config.ThemeManager.getCurrentTheme();
        if (current == com.hamza.account.config.ThemeManager.Theme.DARK) {
            return Style_Sheet.getStyle();
        }
        return Style_Sheet.CSS_SUB_RETURN;
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
        return TOTAL_PUR_RE;
    }

    @Override
    public String nameTextOfInvoice() {
        return Setting_Language.WORD_RE_PUR;
    }

    @Override
    public String nameTextOfReport() {
        return "تقارير مرتجع الموردين";
    }

    @Override
    public UserPermissionType show() {
        return UserPermissionType.PURCHASE_RE_SHOW;
    }

    @Override
    public UserPermissionType update() {
        return UserPermissionType.PURCHASE_RE_UPDATE;
    }

    @Override
    public UserPermissionType delete() {
        return UserPermissionType.PURCHASE_RE_DELETE;
    }

    @Override
    public UserPermissionType show_totals() {
        return UserPermissionType.TOTAL_PURCHASE_RE_SHOW;
    }

    @Override
    public UserPermissionType show_totals_invoice() {
        return UserPermissionType.TOTAL_PURCHASE_RE_SHOW_INVOICE;
    }

}
