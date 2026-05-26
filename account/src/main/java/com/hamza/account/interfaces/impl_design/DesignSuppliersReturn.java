package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.controlsfx.language.Setting_Language.TOTAL_PUR_RE;

public class DesignSuppliersReturn implements DesignInterface {

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

}
