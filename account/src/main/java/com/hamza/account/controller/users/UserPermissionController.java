package com.hamza.account.controller.users;

import com.hamza.account.model.domain.Users_Permission;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.UserPermissionService;
import com.hamza.account.type.UserPermissionType;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.Pane;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "user-permission.fxml")
@RequiredArgsConstructor
public class UserPermissionController implements AppSettingInterface {

    private final int user_id;
    private final String username;
    private final UserPermissionService userPermissionService;

    // purchase
    @FXML
    private CheckBox purchase_show, purchase_update, purchase_delete;
    @FXML
    private CheckBox total_purchase_show, total_purchase_show_invoice;
    @FXML
    private CheckBox purchase_re_show, purchase_re_update;
    @FXML
    private CheckBox purchase_re_delete, total_purchase_re_show, total_purchase_re_show_invoice;

    // sales
    @FXML
    private CheckBox sales_show, sales_update, sales_delete;
    @FXML
    private CheckBox total_sales_show, total_sales_show_invoice;
    @FXML
    private CheckBox sales_re_show, sales_re_update, sales_re_delete;
    @FXML
    private CheckBox total_sales_re_show, total_sales_re_show_invoice;

    // items
    @FXML
    private CheckBox items_show, items_update, items_delete;
    @FXML
    private CheckBox items_add_excel;

    // stocks
    @FXML
    private CheckBox stock_show, stock_update, stock_delete;
    @FXML
    private CheckBox stock_convert_show, stock_convert_update, stock_convert_delete;

    // groups
    @FXML
    private CheckBox main_group_show, main_group_update, main_group_delete;
    @FXML
    private CheckBox sub_group_show, sub_group_update, sub_group_delete;

    // others
    @FXML
    private CheckBox inventory_show;
    @FXML
    private CheckBox treasury_show, treasury_update, treasury_delete;
    @FXML
    private CheckBox units_show, units_update, units_delete;
    @FXML
    private CheckBox sel_price_show, sel_price_update, sel_price_delete;

    // account customer
    @FXML
    private CheckBox customer_show, customer_update, customer_delete;
    @FXML
    private CheckBox customer_account_show, customer_account_update, customer_account_delete;

    // account suppliers
    @FXML
    private CheckBox suppliers_show, suppliers_update, suppliers_delete;
    @FXML
    private CheckBox suppliers_account_show, suppliers_account_update, suppliers_account_delete;

    // others
    @FXML
    private CheckBox expenses_show, expenses_update, expenses_delete;
    @FXML
    private CheckBox employee_show, employee_update, employee_delete;
    @FXML
    private CheckBox setting_show, setting_company_show, setting_backup_show;
    @FXML
    private CheckBox setting_items_show, setting_other_show, setting_shows_show, invoice_profit_show;

    // setting
    @FXML
    private CheckBox employees_show_salary, show_column_buy_price;
    @FXML
    private CheckBox checkEditPreviousData, checkShowPreviousData;
    @FXML
    private CheckBox checkUpdateName, checkUpdatePass;

    // reports
    @FXML
    private CheckBox checkReportSummary, checkReportItems, checkReportCustomers;
    @FXML
    private CheckBox checkReportSuppliers, checkReportCustomAccountArea, checkReportSales;
    @FXML
    private CheckBox checkReportPurchase, checkReportDayDetails, checkReportDelegate, checkReportProfit;


    @FXML
    public void initialize() {
        loadData();
        addNames();
    }

    private void loadData() {
        try {
            var userPermissions = userPermissionService.getUsersPermissionById(user_id);
            HashMap<CheckBox, UserPermissionType> checkBoxMap = mapUserPermissionCheckBox();
            checkBoxMap.forEach((checkBox, userPermissionType) ->
                    checkBox.setSelected(isPermissionGranted(userPermissions, userPermissionType)));
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private boolean isPermissionGranted(List<Users_Permission> userPermissions, UserPermissionType permissionType) {
        return userPermissions.stream().filter(permission ->
                permission.getUserPermissionType().equals(permissionType)).map(Users_Permission::isStatus).findFirst().orElse(false);
    }

    private HashMap<CheckBox, UserPermissionType> mapUserPermissionCheckBox() {
        HashMap<CheckBox, UserPermissionType> checkBoxMap = new HashMap<>();
        checkBoxMap.put(purchase_show, UserPermissionType.PURCHASE_SHOW);
        checkBoxMap.put(purchase_update, UserPermissionType.PURCHASE_UPDATE);
        checkBoxMap.put(purchase_delete, UserPermissionType.PURCHASE_DELETE);
        checkBoxMap.put(total_purchase_show, UserPermissionType.TOTAL_PURCHASE_SHOW);
        checkBoxMap.put(total_purchase_show_invoice, UserPermissionType.TOTAL_PURCHASE_SHOW_INVOICE);
        checkBoxMap.put(purchase_re_show, UserPermissionType.PURCHASE_RE_SHOW);
        checkBoxMap.put(purchase_re_update, UserPermissionType.PURCHASE_RE_UPDATE);
        checkBoxMap.put(purchase_re_delete, UserPermissionType.PURCHASE_RE_DELETE);
        checkBoxMap.put(total_purchase_re_show, UserPermissionType.TOTAL_PURCHASE_RE_SHOW);
        checkBoxMap.put(total_purchase_re_show_invoice, UserPermissionType.TOTAL_PURCHASE_RE_SHOW_INVOICE);
        checkBoxMap.put(sales_show, UserPermissionType.SALES_SHOW);
        checkBoxMap.put(sales_update, UserPermissionType.SALES_UPDATE);
        checkBoxMap.put(sales_delete, UserPermissionType.SALES_DELETE);
        checkBoxMap.put(total_sales_show, UserPermissionType.TOTAL_SALES_SHOW);
        checkBoxMap.put(total_sales_show_invoice, UserPermissionType.TOTAL_SALES_SHOW_INVOICE);
        checkBoxMap.put(sales_re_show, UserPermissionType.SALES_RE_SHOW);
        checkBoxMap.put(sales_re_update, UserPermissionType.SALES_RE_UPDATE);
        checkBoxMap.put(sales_re_delete, UserPermissionType.SALES_RE_DELETE);
        checkBoxMap.put(total_sales_re_show, UserPermissionType.TOTAL_SALES_RE_SHOW);
        checkBoxMap.put(total_sales_re_show_invoice, UserPermissionType.TOTAL_SALES_RE_SHOW_INVOICE);
        checkBoxMap.put(items_show, UserPermissionType.ITEMS_SHOW);
        checkBoxMap.put(items_update, UserPermissionType.ITEMS_UPDATE);
        checkBoxMap.put(items_delete, UserPermissionType.ITEMS_DELETE);
        checkBoxMap.put(items_add_excel, UserPermissionType.ITEMS_ADD_EXCEL);
        checkBoxMap.put(stock_show, UserPermissionType.STOCK_SHOW);
        checkBoxMap.put(stock_update, UserPermissionType.STOCK_UPDATE);
        checkBoxMap.put(stock_delete, UserPermissionType.STOCK_DELETE);
        checkBoxMap.put(stock_convert_show, UserPermissionType.STOCK_CONVERT_SHOW);
        checkBoxMap.put(stock_convert_update, UserPermissionType.STOCK_CONVERT_UPDATE);
        checkBoxMap.put(stock_convert_delete, UserPermissionType.STOCK_CONVERT_DELETE);
        checkBoxMap.put(main_group_show, UserPermissionType.MAIN_GROUP_SHOW);
        checkBoxMap.put(main_group_update, UserPermissionType.MAIN_GROUP_UPDATE);
        checkBoxMap.put(main_group_delete, UserPermissionType.MAIN_GROUP_DELETE);
        checkBoxMap.put(sub_group_show, UserPermissionType.SUB_GROUP_SHOW);
        checkBoxMap.put(sub_group_update, UserPermissionType.SUB_GROUP_UPDATE);
        checkBoxMap.put(sub_group_delete, UserPermissionType.SUB_GROUP_DELETE);
        checkBoxMap.put(inventory_show, UserPermissionType.INVENTORY_SHOW);
        checkBoxMap.put(treasury_show, UserPermissionType.TREASURY_SHOW);
        checkBoxMap.put(treasury_update, UserPermissionType.TREASURY_UPDATE);
        checkBoxMap.put(treasury_delete, UserPermissionType.TREASURY_DELETE);
        checkBoxMap.put(units_show, UserPermissionType.UNITS_SHOW);
        checkBoxMap.put(units_update, UserPermissionType.UNITS_UPDATE);
        checkBoxMap.put(units_delete, UserPermissionType.UNITS_DELETE);
        checkBoxMap.put(sel_price_show, UserPermissionType.SEL_PRICE_SHOW);
        checkBoxMap.put(sel_price_update, UserPermissionType.SEL_PRICE_UPDATE);
        checkBoxMap.put(sel_price_delete, UserPermissionType.SEL_PRICE_DELETE);
        checkBoxMap.put(customer_show, UserPermissionType.CUSTOMER_SHOW);
        checkBoxMap.put(customer_update, UserPermissionType.CUSTOMER_UPDATE);
        checkBoxMap.put(customer_delete, UserPermissionType.CUSTOMER_DELETE);
        checkBoxMap.put(customer_account_show, UserPermissionType.CUSTOMER_ACCOUNT_SHOW);
        checkBoxMap.put(customer_account_update, UserPermissionType.CUSTOMER_ACCOUNT_UPDATE);
        checkBoxMap.put(customer_account_delete, UserPermissionType.CUSTOMER_ACCOUNT_DELETE);
        checkBoxMap.put(suppliers_show, UserPermissionType.SUPPLIERS_SHOW);
        checkBoxMap.put(suppliers_update, UserPermissionType.SUPPLIERS_UPDATE);
        checkBoxMap.put(suppliers_delete, UserPermissionType.SUPPLIERS_DELETE);
        checkBoxMap.put(suppliers_account_show, UserPermissionType.SUPPLIERS_ACCOUNT_SHOW);
        checkBoxMap.put(suppliers_account_update, UserPermissionType.SUPPLIERS_ACCOUNT_UPDATE);
        checkBoxMap.put(suppliers_account_delete, UserPermissionType.SUPPLIERS_ACCOUNT_DELETE);
        checkBoxMap.put(expenses_show, UserPermissionType.EXPENSES_SHOW);
        checkBoxMap.put(expenses_update, UserPermissionType.EXPENSES_UPDATE);
        checkBoxMap.put(expenses_delete, UserPermissionType.EXPENSES_DELETE);
        checkBoxMap.put(employee_show, UserPermissionType.EMPLOYEE_SHOW);
        checkBoxMap.put(employee_update, UserPermissionType.EMPLOYEE_UPDATE);
        checkBoxMap.put(employee_delete, UserPermissionType.EMPLOYEE_DELETE);
        checkBoxMap.put(setting_show, UserPermissionType.SETTING_SHOW);
        checkBoxMap.put(setting_company_show, UserPermissionType.SETTING_COMPANY_SHOW);
        checkBoxMap.put(setting_backup_show, UserPermissionType.SETTING_BACKUP_SHOW);
        checkBoxMap.put(setting_items_show, UserPermissionType.SETTING_ITEMS_SHOW);
        checkBoxMap.put(setting_other_show, UserPermissionType.SETTING_OTHER_SHOW);
        checkBoxMap.put(setting_shows_show, UserPermissionType.SETTING_SHOWS_SHOW);
        checkBoxMap.put(invoice_profit_show, UserPermissionType.INVOICE_PROFIT_SHOW);
        checkBoxMap.put(employees_show_salary, UserPermissionType.EMPLOYEES_SHOW_SALARY);
        checkBoxMap.put(show_column_buy_price, UserPermissionType.SHOW_COLUMN_BUY_PRICE);
        checkBoxMap.put(checkEditPreviousData, UserPermissionType.UPDATE_DATA_BEFORE_MONTH);
        checkBoxMap.put(checkShowPreviousData, UserPermissionType.SHOW_DATA_BEFORE_MONTH);
        getChecks(checkBoxMap);
        return checkBoxMap;
    }

    private void getChecks(HashMap<CheckBox, UserPermissionType> checkBoxMap) {
        CheckBox[] checkBoxList = {checkUpdateName, checkUpdatePass, checkReportSummary, checkReportItems, checkReportCustomers, checkReportSuppliers
                , checkReportCustomAccountArea, checkReportSales, checkReportPurchase, checkReportDayDetails
                , checkReportDelegate, checkReportProfit};
        int h = 0;
        for (int i = 76; i < 88; i++) {
            var userPermissionById = UserPermissionType.getUserPermissionById(i);
            checkBoxMap.put(checkBoxList[h], userPermissionById);
            h++;
        }
    }

    private void addNames() {
        purchase_show.setText("عرض الشراء");
        purchase_update.setText("تعديل الشراء");
        purchase_delete.setText("حذف الشراء");
        total_purchase_show.setText("عرض إجمالي الشراء");
        total_purchase_show_invoice.setText("عرض فاتورة إجمالي الشراء");
        purchase_re_show.setText("عرض مرتجعات الشراء");
        purchase_re_update.setText("تعديل مرتجعات الشراء");
        purchase_re_delete.setText("حذف مرتجعات الشراء");
        total_purchase_re_show.setText("عرض إجمالي مرتجعات الشراء");
        total_purchase_re_show_invoice.setText("عرض فاتورة إجمالي مرتجعات الشراء");
        sales_show.setText("عرض المبيعات");
        sales_update.setText("تعديل المبيعات");
        sales_delete.setText("حذف المبيعات");
        total_sales_show.setText("عرض إجمالي المبيعات");
        total_sales_show_invoice.setText("عرض فاتورة إجمالي المبيعات");
        sales_re_show.setText("عرض مرتجعات المبيعات");
        sales_re_update.setText("تعديل مرتجعات المبيعات");
        sales_re_delete.setText("حذف مرتجعات المبيعات");
        total_sales_re_show.setText("عرض إجمالي مرتجعات المبيعات");
        total_sales_re_show_invoice.setText("عرض فاتورة إجمالي مرتجعات المبيعات");
        items_show.setText("عرض الأصناف");
        items_update.setText("تعديل الأصناف");
        items_delete.setText("حذف الأصناف");
        items_add_excel.setText("إضافة الأصناف من ملف Excel");
        stock_show.setText("عرض المخزون");
        stock_update.setText("تعديل المخزون");
        stock_delete.setText("حذف المخزون");
        stock_convert_show.setText("عرض تحويلات المخزون");
        stock_convert_update.setText("تعديل تحويلات المخزون");
        stock_convert_delete.setText("حذف تحويلات المخزون");
        main_group_show.setText("عرض المجموعة الرئيسية");
        main_group_update.setText("تعديل المجموعة الرئيسية");
        main_group_delete.setText("حذف المجموعة الرئيسية");
        sub_group_show.setText("عرض المجموعة الفرعية");
        sub_group_update.setText("تعديل المجموعة الفرعية");
        sub_group_delete.setText("حذف المجموعة الفرعية");
        inventory_show.setText("عرض الجرد");
        treasury_show.setText("عرض الخزينة");
        treasury_update.setText("تعديل الخزينة");
        treasury_delete.setText("حذف الخزينة");
        units_show.setText("عرض الوحدات");
        units_update.setText("تعديل الوحدات");
        units_delete.setText("حذف الوحدات");
        sel_price_show.setText("عرض أسعار البيع");
        sel_price_update.setText("تعديل أسعار البيع");
        sel_price_delete.setText("حذف أسعار البيع");
        customer_show.setText("عرض العملاء");
        customer_update.setText("تعديل العملاء");
        customer_delete.setText("حذف العملاء");
        customer_account_show.setText("عرض حساب العملاء");
        customer_account_update.setText("تعديل حساب العملاء");
        customer_account_delete.setText("حذف حساب العملاء");
        suppliers_show.setText("عرض الموردين");
        suppliers_update.setText("تعديل الموردين");
        suppliers_delete.setText("حذف الموردين");
        suppliers_account_show.setText("عرض حساب الموردين");
        suppliers_account_update.setText("تعديل حساب الموردين");
        suppliers_account_delete.setText("حذف حساب الموردين");
        expenses_show.setText("عرض المصروفات");
        expenses_update.setText("تعديل المصروفات");
        expenses_delete.setText("حذف المصروفات");
        employee_show.setText("عرض الموظفين");
        employee_update.setText("تعديل الموظفين");
        employee_delete.setText("حذف الموظفين");
        setting_show.setText("عرض الإعدادات");
        setting_company_show.setText("عرض إعدادات الشركة");
        setting_backup_show.setText("عرض نسخ البيانات الاحتياطية");
        setting_items_show.setText("عرض إعدادات الأصناف");
        setting_other_show.setText("عرض الإعدادات الأخرى");
        setting_shows_show.setText("عرض إعدادات الشاشات");
        invoice_profit_show.setText("عرض نسبة الربح من الفواتير");
        employees_show_salary.setText("إظهار المرتب");
        show_column_buy_price.setText("عرض سعر الشراء");
    }

    @Override
    public int save() throws DaoException {
        List<Users_Permission> userPermissions = new ArrayList<>();
        HashMap<CheckBox, UserPermissionType> checkBoxMap = mapUserPermissionCheckBox();
        checkBoxMap.forEach((checkBox, userPermissionType) -> {
            userPermissions.add(new Users_Permission(0, user_id, userPermissionType, checkBox.isSelected()));
        });
        var i = userPermissionService.updateUserPermissionsList(userPermissions);
        log.info("updateUserPermissionsList: {}", i);
        return i;
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return Setting_Language.WORD_PERM.concat(" / ").concat(username);
    }

    @Override
    public boolean addLastPane() {
        return true;
    }
}
