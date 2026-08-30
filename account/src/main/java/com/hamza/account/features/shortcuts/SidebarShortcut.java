package com.hamza.account.features.shortcuts;

/** Stable preference identifiers for every actionable command in the main sidebar. */
public enum SidebarShortcut {
    SALES("Ctrl+1"), SALES_RETURN(""), TOTAL_SALES(""), TOTAL_SALES_RETURN(""),
    PURCHASE("Ctrl+2"), PURCHASE_RETURN(""), TOTAL_PURCHASE(""), TOTAL_PURCHASE_RETURN(""),
    ITEMS("Ctrl+3"), ADD_ITEM(""), UNITS(""), MAIN_GROUP(""), SUB_GROUP(""), AREA(""), INVENTORY(""), STOCK_COUNT(""), STOCKS(""), STOCK_TRANSFERS(""), MERGE_ITEMS(""),
    ADD_CUSTOMER(""), CUSTOMERS("Ctrl+4"), CUSTOMER_ACCOUNT(""),
    ADD_SUPPLIER(""), SUPPLIERS("Ctrl+5"), SUPPLIER_ACCOUNT(""),
    ADD_EMPLOYEE(""), EMPLOYEES("Ctrl+6"), ADD_USER(""), USERS(""),
    TREASURIES(""), TREASURY_TRANSFER(""), TREASURY_CASH(""), TREASURY_CAPITAL(""), TREASURY_DETAILS("Ctrl+7"), TREASURY_PROCESS(""), EXPENSES(""),
    REPORT_SUMMARY("Ctrl+8"), REPORT_ITEMS(""), REPORT_ITEMS_DAILY(""), REPORT_SALES_YEAR(""), REPORT_PURCHASE_YEAR(""), REPORT_CUSTOMER_PAID(""), REPORT_SUPPLIER_PAID(""), REPORT_DETAILS(""), REPORT_YEARLY(""), REPORT_PROFIT_LOSS(""), REPORT_RETURN_REASONS(""),
    HOME("Ctrl+H"), SETTINGS("Ctrl+S"), SHIFT_REPORTS(""), BACKUP("Ctrl+B"), DELETE_DATA(""), ABOUT(""), CLOSE(""), YOUTUBE("");

    private final String defaultCombination;

    SidebarShortcut(String defaultCombination) {
        this.defaultCombination = defaultCombination;
    }

    public String defaultCombination() {
        return defaultCombination;
    }
}