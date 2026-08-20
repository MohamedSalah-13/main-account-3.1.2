package com.hamza.account.interfaces.impl_design;

import com.hamza.account.document.DocumentType;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.controlsfx.language.LanguageManager;

public class DesignSuppliers implements DesignInterface {

    @Override
    public DocumentType documentType() {
        return DocumentType.PURCHASE;
    }

    @Override
    public String nameTextOfData() {
        return LanguageManager.getInstance().getString("suppliers");
    }

    @Override
    public String nameTextOfAccount() {
        return LanguageManager.getInstance().getString("supAcc");
    }

    @Override
    public String nameTextOfTotal() {
        return LanguageManager.getInstance().getString("setting.total.purchase");
    }

    @Override
    public String nameTextOfInvoice() {
        return LanguageManager.getInstance().getString("pur");
    }

    @Override
    public String nameTextOfReport() {
        return LanguageManager.getInstance().getString("setting.report.supplier");
    }

}
