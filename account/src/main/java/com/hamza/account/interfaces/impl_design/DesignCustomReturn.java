package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.controlsfx.language.Setting_Language;

import static com.hamza.controlsfx.language.Setting_Language.TOTAL_SALES_RE;

public class DesignCustomReturn implements DesignInterface {

    @Override
    public DocumentType documentType() {
        return DocumentType.SALES_RETURN;
    }

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

}
