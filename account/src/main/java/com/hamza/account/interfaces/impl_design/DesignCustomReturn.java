package com.hamza.account.interfaces.impl_design;

import com.hamza.account.config.ThemeManager;
import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.controlsfx.language.LanguageManager;

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
        return LanguageManager.getInstance().getString("customers");
    }

    @Override
    public String nameTextOfAccount() {
        return LanguageManager.getInstance().getString("cuAcc");
    }

    @Override
    public String nameTextOfTotal() {
        return LanguageManager.getInstance().getString("setting.total.sales.return");
    }

    @Override
    public String nameTextOfInvoice() {
        return LanguageManager.getInstance().getString("ReSal");
    }

    @Override
    public String nameTextOfReport() {
        return LanguageManager.getInstance().getString("setting.report.customer.return");
    }

}
