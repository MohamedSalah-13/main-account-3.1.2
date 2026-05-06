package com.hamza.account.type;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TableType {

    COMPANY(Setting_Language.company),
    CUSTOM(Setting_Language.WORD_CUSTOM),
    CUSTOMER_ACC(Setting_Language.WORD_CUSTOM_ACC),
    EMPLOYEES(Setting_Language.EMPLOYEES),
    EXPENSES(Setting_Language.EXPENSES),
    GROUP_MAIN(Setting_Language.WORD_MAIN_G),
    GROUP_SUB(Setting_Language.WORD_SUB_G),
    ITEMS(Setting_Language.WORD_ITEMS),
    STOCKS(Setting_Language.WORD_STOCK),
    SUPPLIERS(Setting_Language.WORD_SUP),
    SUPPLIERS_ACCOUNT(Setting_Language.WORD_SUP_ACC),
    TOTAL_BUY(Setting_Language.TOTAL_PUR),
    TOTAL_BUY_RETURN(Setting_Language.WORD_RE_PUR),
    TOTAL_SALES(Setting_Language.TOTAL_SALES),
    TOTAL_SALES_RETURN(Setting_Language.WORD_RE_SALES),
    TREASURY(Setting_Language.TREASURY),
    UNITS(Setting_Language.UNITS),
    USERS(Setting_Language.WORD_USERS),
    STOCK_TRANSFER(Setting_Language.STORE_TRANSFERS),
    TREASURY_TRANSFERS(Setting_Language.TREASURY_TRANSFERS),
    ITEMS_UNITS("وحدات الصنف"),
    NOT_USED("not used");

    private final StringProperty type;

    TableType(String type) {
        this.type = new SimpleStringProperty(type);
    }

    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }
}
