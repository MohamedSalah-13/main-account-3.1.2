package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.setting.FontColorDialog;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.NumberGenerator;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@Getter
public class MainMenuController {

    @FXML
    private Menu menuAccounts, menuSales, menuPurchase, menuCustomer, menuSupplier, menuEmployees, menuSetting, menuItems, menuReport, menuExpenses;
    @FXML
    private MenuItem menuItemTreeAccounts, menuItemAllAccounts, menuItemGroupAccounts, menuItemBillExchange;
    @FXML
    private MenuItem menuItemSales, menuItemSalesReturn, menuItemTotalSales, menuItemTotalSalesReturn;
    @FXML
    private MenuItem menuItemPurchase, menuItemPurchaseReturn, menuItemTotalPurchase, menuItemTotalPurchaseReturn;
    @FXML
    private MenuItem menuItemItems, menuItemAddItem, menuItemAddItemFromExcel, menuItemUnit, menuItemInventory, menuItemMainGroup, menuItemSupGroup, menuItemConvertStock;
    @FXML
    private MenuItem menuItemAddCustomName, menuItemCustomName, menuItemCustomAccount;
    @FXML
    private MenuItem menuItemAddSupplierName, menuItemSuppliersName, menuItemSuppliersAccount;
    @FXML
    private MenuItem menuItemSummary, menuItemReportItems,
            menuItemReportItemsDaily, menuItemReportCustom, menuItemCustomPaid, menuItemReportSuppliers, menuItemSuppliersPaid, menuItemReportSales, menuItemReportPurchase, menuItemReportDetails, menuItemReportDelegate, menuItemReportYearly, menuItemReportProfitLoss;
    @FXML
    private MenuItem menuItemAddUser, menuItemUsers, menuItemAddEmployee, menuItemEmployees, menuItemAddTargetDelegate;
    @FXML
    private MenuItem menuItemAddExpenseName, menuItemExpensesName, menuItemAddExpense, menuItemAllExpenses, menuItemExpensesDetails;
    @FXML
    private MenuItem menuItemHome, menuItemSettingUsers, menuItemDeleteData, menuItemBackup, menuItemCheckUpdate, menuItemRegister, menuItemAbout, menuItemClose, menuFontColor, menuNumber;
    @FXML
    private MenuItem menuItemReportSalesByYear, menuItemReportPurchaseByYear;
    @FXML
    private MenuBar menuBar;

    @FXML
    private MenuItem menuItemShiftReports;

    @FXML
    public void initialize() {
        otherSetting();
        setGraphicMenu();
        setGraphicMenuItems();

        // disable menu
        menuAccounts.setVisible(false);
        menuExpenses.setVisible(false);
        menuFontColor.setVisible(false);
        menuNumber.setVisible(false);
        menuItemRegister.setVisible(false);
        menuItemCheckUpdate.setVisible(false);
        menuItemShiftReports.setVisible(false);

        menuItemReportCustom.setVisible(false);
        menuItemReportSuppliers.setVisible(false);

        // ✅ تطبيق الصلاحيات على عناصر القائمة
        applyPermissions();

        // ✅ تأجيل إخفاء القوائم الفارغة إلى بعد اكتمال التحميل
        Platform.runLater(this::hideEmptyMenus);
    }

    /**
     * ✅ تطبيق الصلاحيات على جميع عناصر القائمة
     */
    private void applyPermissions() {
        // قائمة المبيعات
        applySalesPermissions();

        // قائمة المشتريات
        applyPurchasePermissions();

        // قائمة الأصناف
        applyItemsPermissions();

        // قائمة العملاء
        applyCustomerPermissions();

        // قائمة الموردين
        applySupplierPermissions();

        // قائمة التقارير
        applyReportsPermissions();

        // قائمة الموظفين
        applyEmployeesPermissions();

        // قائمة الإعدادات
        applySettingsPermissions();

        log.info("تم تطبيق الصلاحيات على القائمة الرئيسية");
    }

    private void applySalesPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemSales, PermissionCode.SALES_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemSalesReturn, PermissionCode.TOTAL_SALES_RE_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemTotalSales, PermissionCode.TOTAL_SALES_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemTotalSalesReturn, PermissionCode.TOTAL_SALES_RE_SHOW);
    }

    private void applyPurchasePermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemPurchase, PermissionCode.TOTAL_PURCHASE_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemPurchaseReturn, PermissionCode.TOTAL_PURCHASE_RE_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemTotalPurchase, PermissionCode.TOTAL_PURCHASE_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemTotalPurchaseReturn, PermissionCode.TOTAL_PURCHASE_RE_SHOW);
    }

    private void applyItemsPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemItems, PermissionCode.ITEMS_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemAddItem, PermissionCode.ITEMS_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemAddItemFromExcel, PermissionCode.ITEMS_IMPORT);
        PermissionHelper.hideIfNotAllowed(menuItemUnit, PermissionCode.UNITS_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemInventory, PermissionCode.STOCK_ADJUSTMENT);
        PermissionHelper.hideIfNotAllowed(menuItemMainGroup, PermissionCode.MAIN_GROUP_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemSupGroup, PermissionCode.SUB_GROUP_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemConvertStock, PermissionCode.STOCK_TRANSFER);
    }

    private void applyCustomerPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemAddCustomName, PermissionCode.CUSTOMERS_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemCustomName, PermissionCode.CUSTOMERS_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemCustomAccount, PermissionCode.CUSTOMERS_ACCOUNT_SHOW);
    }

    private void applySupplierPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemAddSupplierName, PermissionCode.SUPPLIERS_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemSuppliersName, PermissionCode.SUPPLIERS_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemSuppliersAccount, PermissionCode.SUPPLIERS_ACCOUNT_SHOW);
    }

    private void applyReportsPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemSummary, PermissionCode.REPORTS_DASHBOARD);
        PermissionHelper.hideIfNotAllowed(menuItemReportItems, PermissionCode.REPORTS_INVENTORY);
        PermissionHelper.hideIfNotAllowed(menuItemReportItemsDaily, PermissionCode.REPORTS_INVENTORY_MOVEMENT);
        PermissionHelper.hideIfNotAllowed(menuItemReportSalesByYear, PermissionCode.REPORTS_SALES_MONTHLY);
        PermissionHelper.hideIfNotAllowed(menuItemReportPurchaseByYear, PermissionCode.REPORTS_PURCHASES_MONTHLY);
        PermissionHelper.hideIfNotAllowed(menuItemCustomPaid, PermissionCode.REPORTS_CUSTOMERS_RECEIVABLES);
        PermissionHelper.hideIfNotAllowed(menuItemSuppliersPaid, PermissionCode.REPORTS_SUPPLIERS_PAYABLES);
        PermissionHelper.hideIfNotAllowed(menuItemReportDetails, PermissionCode.REPORTS_ACCOUNT_STATEMENT);
        PermissionHelper.hideIfNotAllowed(menuItemReportDelegate, PermissionCode.REPORTS_PROFIT_BY_ITEM);
        PermissionHelper.hideIfNotAllowed(menuItemReportYearly, PermissionCode.REPORTS_SALES_COMPREHENSIVE);
        PermissionHelper.hideIfNotAllowed(menuItemReportProfitLoss, PermissionCode.REPORTS_PROFIT);
        PermissionHelper.hideIfNotAllowed(menuItemAllExpenses, PermissionCode.EXPENSES_SHOW);
    }

    private void applyEmployeesPermissions() {
        PermissionHelper.hideIfNotAllowed(menuItemUsers, PermissionCode.USERS_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemAddUser, PermissionCode.USERS_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemAddEmployee, PermissionCode.EMPLOYEES_CREATE);
        PermissionHelper.hideIfNotAllowed(menuItemEmployees, PermissionCode.EMPLOYEES_SHOW);
        PermissionHelper.hideIfNotAllowed(menuItemAddTargetDelegate, PermissionCode.TARGETS_CREATE);
    }

    private void applySettingsPermissions() {
        // menuItemHome - متاح للجميع
        PermissionHelper.hideIfNotAllowed(menuItemSettingUsers, PermissionCode.SETTINGS_SYSTEM);
        PermissionHelper.hideIfNotAllowed(menuItemDeleteData, PermissionCode.SETTINGS_SYSTEM);
        PermissionHelper.hideIfNotAllowed(menuItemBackup, PermissionCode.SETTINGS_BACKUP);
        // menuItemAbout, menuItemClose - متاح للجميع

        // الورديات
        if (PermissionHelper.has(PermissionCode.SHIFTS_REPORTS) || PermissionHelper.has(PermissionCode.SHIFTS_ADMIN)) {
            menuItemShiftReports.setVisible(true);
        }
    }

    /**
     * إخفاء القوائم الرئيسية التي لا تحتوي على عناصر مرئية
     * ✅ يتم استدعاؤها بعد اكتمال تحميل جميع القوائم
     */
    private void hideEmptyMenus() {
        hideMenuIfEmpty(menuSales);
        hideMenuIfEmpty(menuPurchase);
        hideMenuIfEmpty(menuItems);
        hideMenuIfEmpty(menuCustomer);
        hideMenuIfEmpty(menuSupplier);
        hideMenuIfEmpty(menuEmployees);
        hideMenuIfEmpty(menuReport);
        hideMenuIfEmpty(menuExpenses);
        // menuSetting - دائماً مرئي (على الأقل يحتوي على Home & About & Close)

        log.info("تم إخفاء القوائم الفارغة");
    }

    /**
     * إخفاء القائمة إذا كانت جميع عناصرها مخفية
     */
    private void hideMenuIfEmpty(Menu menu) {
        boolean hasVisibleItem = menu.getItems().stream()
                .anyMatch(MenuItem::isVisible);

        if (!hasVisibleItem) {
            menu.setVisible(false);
            log.debug("تم إخفاء القائمة: {}", menu.getText());
        }
    }

    private void otherSetting() {
        menuAccounts.setText(Setting_Language.WORD_ACCOUNT);
        menuSales.setText(Setting_Language.WORD_SALES);
        menuPurchase.setText(Setting_Language.WORD_PUR);
        menuItems.setText(Setting_Language.WORD_ITEMS);
        menuCustomer.setText(Setting_Language.WORD_CUSTOM);
        menuSupplier.setText(Setting_Language.WORD_SUP);
        menuSetting.setText(Setting_Language.WORD_SETTING);
        menuEmployees.setText(Setting_Language.EMPLOYEES);
        menuReport.setText(Setting_Language.WORD_REPORT);
        menuExpenses.setText(Setting_Language.EXPENSES);

        menuFontColor.setText("Font and Color Settings");
        menuFontColor.setOnAction(event -> openFontColorDialog());
        menuNumber.setOnAction(event -> new NumberGenerator().start(new Stage()));

    }

    private void openFontColorDialog() {
        try {
            FontColorDialog dialog = new FontColorDialog();
            dialog.showAndWait();
        } catch (Exception e) {
            AllAlerts.alertError("Error opening font and color settings : " + e.getMessage());
        }
    }


    private void setGraphicMenuItems() {
        var images = new Image_Setting();
        menuItemHome.setGraphic(createIcon(images.homeWhite));
        menuItemSettingUsers.setGraphic(createIcon(images.setting));
        menuItemDeleteData.setGraphic(createIcon(images.cancel));
        menuItemBackup.setGraphic(createIcon(images.database));
        menuItemCheckUpdate.setGraphic(createIcon(images.update));
        menuItemClose.setGraphic(createIcon(images.exit));
        menuItemRegister.setGraphic(createIcon(images.option));
        menuItemAbout.setGraphic(createIcon(images.about));
        menuFontColor.setGraphic(createIcon(images.font));
        menuNumber.setGraphic(createIcon(images.show));

        menuFontColor.setAccelerator(KeyCombination.keyCombination("Ctrl+F"));
    }

    private void setGraphicMenu() {
        var images = new Image_Setting();
        menuSales.setGraphic(createIcon(images.shoppingSales));
        menuPurchase.setGraphic(createIcon(images.shoppingPurchase));
        menuItems.setGraphic(createIcon(images.itemWhite));
        menuReport.setGraphic(createIcon(images.reports));
        menuSetting.setGraphic(createIcon(images.setting));
        menuSupplier.setGraphic(createIcon(images.personSup));
        menuCustomer.setGraphic(createIcon(images.personCustomer));
    }

    /**
     * ✅ دالة عامة لإعادة فحص وإخفاء القوائم الفارغة
     * يمكن استدعاؤها من MainScreenController بعد اكتمال التحميل
     */
    public void finalizeMenuVisibility() {
        Platform.runLater(this::hideEmptyMenus);
    }
}