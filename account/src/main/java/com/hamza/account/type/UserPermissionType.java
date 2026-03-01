package com.hamza.account.type;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserPermissionType {

    //TODO 10/7/2025 7:46 AM Mohamed: update column permission_id as string in database
    PURCHASE_SHOW(1, "purchase_show"),
    PURCHASE_UPDATE(2, "purchase_update"),
    PURCHASE_DELETE(3, "purchase_delete"),
    TOTAL_PURCHASE_SHOW(4, "total_purchase_show"),
    TOTAL_PURCHASE_SHOW_INVOICE(5, "total_purchase_show_invoice"),
    PURCHASE_RE_SHOW(6, "purchase_re_show"),
    PURCHASE_RE_UPDATE(7, "purchase_re_update"),
    PURCHASE_RE_DELETE(8, "purchase_re_delete"),
    TOTAL_PURCHASE_RE_SHOW(9, "total_purchase_re_show"),
    TOTAL_PURCHASE_RE_SHOW_INVOICE(10, "total_purchase_re_show_invoice"),
    SALES_SHOW(11, "sales_show"),
    SALES_UPDATE(12, "sales_update"),
    SALES_DELETE(13, "sales_delete"),
    TOTAL_SALES_SHOW(14, "total_sales_show"),
    TOTAL_SALES_SHOW_INVOICE(15, "total_sales_show_invoice"),
    SALES_RE_SHOW(16, "sales_re_show"),
    SALES_RE_UPDATE(17, "sales_re_update"),
    SALES_RE_DELETE(18, "sales_re_delete"),
    TOTAL_SALES_RE_SHOW(19, "total_sales_re_show"),
    TOTAL_SALES_RE_SHOW_INVOICE(20, "total_sales_re_show_invoice"),
    ITEMS_SHOW(21, "items_show"),
    ITEMS_UPDATE(22, "items_update"),
    ITEMS_DELETE(23, "items_delete"),
    ITEMS_ADD_EXCEL(24, "items_add_excel"),
    STOCK_SHOW(25, "stock_show"),
    STOCK_UPDATE(26, "stock_update"),
    STOCK_DELETE(27, "stock_delete"),
    STOCK_CONVERT_SHOW(28, "stock_convert_show"),
    STOCK_CONVERT_UPDATE(29, "stock_convert_update"),
    STOCK_CONVERT_DELETE(30, "stock_convert_delete"),
    MAIN_GROUP_SHOW(31, "main_group_show"),
    MAIN_GROUP_UPDATE(32, "main_group_update"),
    MAIN_GROUP_DELETE(33, "main_group_delete"),
    SUB_GROUP_SHOW(34, "sub_group_show"),
    SUB_GROUP_UPDATE(35, "sub_group_update"),
    SUB_GROUP_DELETE(36, "sub_group_delete"),
    INVENTORY_SHOW(37, "inventory_show"),
    TREASURY_SHOW(38, "treasury_show"),
    TREASURY_UPDATE(39, "treasury_update"),
    TREASURY_DELETE(40, "treasury_delete"),
    UNITS_SHOW(41, "units_show"),
    UNITS_UPDATE(42, "units_update"),
    UNITS_DELETE(43, "units_delete"),
    SEL_PRICE_SHOW(44, "sel_price_show"),
    SEL_PRICE_UPDATE(45, "sel_price_update"),
    SEL_PRICE_DELETE(46, "sel_price_delete"),
    CUSTOMER_SHOW(47, "customer_show"),
    CUSTOMER_UPDATE(48, "customer_update"),
    CUSTOMER_DELETE(49, "customer_delete"),
    CUSTOMER_ACCOUNT_SHOW(50, "customer_account_show"),
    CUSTOMER_ACCOUNT_UPDATE(51, "customer_account_update"),
    CUSTOMER_ACCOUNT_DELETE(52, "customer_account_delete"),
    SUPPLIERS_SHOW(53, "suppliers_show"),
    SUPPLIERS_UPDATE(54, "suppliers_update"),
    SUPPLIERS_DELETE(55, "suppliers_delete"),
    SUPPLIERS_ACCOUNT_SHOW(56, "suppliers_account_show"),
    SUPPLIERS_ACCOUNT_UPDATE(57, "suppliers_account_update"),
    SUPPLIERS_ACCOUNT_DELETE(58, "suppliers_account_delete"),
    EXPENSES_SHOW(59, "expenses_show"),
    EXPENSES_UPDATE(60, "expenses_update"),
    EXPENSES_DELETE(61, "expenses_delete"),
    EMPLOYEE_SHOW(62, "employee_show"),
    EMPLOYEE_UPDATE(63, "employee_update"),
    EMPLOYEE_DELETE(64, "employee_delete"),
    SETTING_SHOW(65, "setting_show"),
    SETTING_COMPANY_SHOW(66, "setting_company_show"),
    SETTING_BACKUP_SHOW(67, "setting_backup_show"),
    SETTING_OTHER_SHOW(68, "setting_other_show"),
    SETTING_ITEMS_SHOW(69, "setting_items_show"),
    SETTING_SHOWS_SHOW(70, "setting_shows_show"),
    INVOICE_PROFIT_SHOW(71, "invoice_profit_show"),
    EMPLOYEES_SHOW_SALARY(72, "employees_show_salary"),
    SHOW_COLUMN_BUY_PRICE(73, "show_column_buy_price"),
    UPDATE_DATA_BEFORE_MONTH(74, "update_data_before_month"),
    SHOW_DATA_BEFORE_MONTH(75, "show_data_before_month"),
    SETTING_UPDATE_NAME(76, "setting_update_name"),
    SETTING_UPDATE_PASS(77, "setting_update_pass"),
    REPORTS_SHOW_SUMMARY(78, "reports_show_summary"),
    REPORTS_SHOW_ITEMS(79, "reports_show_items"),
    REPORTS_SHOW_CUSTOMERS(80, "reports_show_customers"),
    REPORTS_SHOW_SUPPLIERS(81, "reports_show_suppliers"),
    REPORTS_SHOW_CUSTOMERS_ACCOUNT_AREA(82, "reports_show_customers_account_area"),
    REPORTS_SHOW_SALES(83, "reports_show_sales"),
    REPORTS_SHOW_PURCHASE(84, "reports_show_purchase"),
    REPORTS_SHOW_DAY_DETAILS(85, "reports_show_day_details"),
    REPORTS_SHOW_DELEGATE(86, "reports_show_delegate"),
    REPORTS_SHOW_PROFIT(87, "reports_show_profit"),
    DISABLE_BUTTON(1000, "reports_show_profit");


    private final IntegerProperty id;
    private final StringProperty type;

    UserPermissionType(int id, String type) {
        this.id = new SimpleIntegerProperty(id);
        this.type = new SimpleStringProperty(type);
    }

    public static UserPermissionType getUserPermissionById(int id) {
        for (UserPermissionType userType : UserPermissionType.values()) {
            if (userType.getId() == id) {
                return userType;
            }
        }
        return null;

    }

    public static UserPermissionType getUserPermissionType(String type) {
        for (UserPermissionType userType : UserPermissionType.values()) {
            if (userType.getType().equals(type)) {
                return userType;
            }
        }
        return null;

    }

    public int getId() {
        return id.get();
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getType() {
        return type.get();
    }

    public StringProperty typeProperty() {
        return type;
    }

}
