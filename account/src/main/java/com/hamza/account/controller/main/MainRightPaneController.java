package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
public class MainRightPaneController implements Initializable {

    @Getter
    @FXML
    private Button btnSales, btnTotalSale, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems,
            btnAddItem, btnUnits, btnMainGroup, btnInventory, btnCustomer, btnAccountCustom, btnSuppliers,
            btnAccountSuppliers,
            btnTreasuryDetails, btnConvertTreasury, btnProcess, btnExpenses, btnHome, btnUsers, btnBackup, btnClose;
    @FXML
    private TitledPane paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneSetting;
    @FXML
    private Text txtNameProject, txtName, txtTel;
    @FXML
    private MenuItem menuItemChangeName, menuItemChangePass, menuItemLogout;
    @FXML
    private MenuButton menuButton;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        // ✅ تطبيق الصلاحيات على أزرار القائمة الجانبية
        applyPermissions();
    }

    /**
     * ✅ تطبيق الصلاحيات على جميع الأزرار
     */
    private void applyPermissions() {
        // المبيعات
        PermissionHelper.hideIfNotAllowed(btnSales, PermissionCode.TOTAL_SALES_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalSale, PermissionCode.TOTAL_SALES_SHOW);

        // المشتريات
        PermissionHelper.hideIfNotAllowed(btnPurchase, PermissionCode.TOTAL_PURCHASE_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalPurchase, PermissionCode.TOTAL_PURCHASE_SHOW);
        PermissionHelper.hideIfNotAllowed(btnPurchaseRe, PermissionCode.TOTAL_PURCHASE_RE_CREATE);
        PermissionHelper.hideIfNotAllowed(btnTotalPurchaseRe, PermissionCode.TOTAL_PURCHASE_RE_SHOW);

        // الأصناف
        PermissionHelper.hideIfNotAllowed(btnItems, PermissionCode.ITEMS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAddItem, PermissionCode.ITEMS_CREATE);
        PermissionHelper.hideIfNotAllowed(btnUnits, PermissionCode.UNITS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnMainGroup, PermissionCode.MAIN_GROUP_SHOW);
        PermissionHelper.hideIfNotAllowed(btnInventory, PermissionCode.STOCK_ADJUSTMENT);

        // العملاء
        PermissionHelper.hideIfNotAllowed(btnCustomer, PermissionCode.CUSTOMERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAccountCustom, PermissionCode.CUSTOMERS_ACCOUNT_SHOW);

        // الموردين
        PermissionHelper.hideIfNotAllowed(btnSuppliers, PermissionCode.SUPPLIERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnAccountSuppliers, PermissionCode.SUPPLIERS_ACCOUNT_SHOW);

        // الخزائن
        PermissionHelper.hideIfNotAllowed(btnTreasuryDetails, PermissionCode.TREASURY_SHOW);
        PermissionHelper.hideIfNotAllowed(btnConvertTreasury, PermissionCode.TREASURY_TRANSFER);
        PermissionHelper.hideIfNotAllowed(btnProcess, PermissionCode.TREASURY_WITHDRAW);
        PermissionHelper.hideIfNotAllowed(btnExpenses, PermissionCode.EXPENSES_SHOW);

        // الإعدادات
        // btnHome - متاح للجميع
        PermissionHelper.hideIfNotAllowed(btnUsers, PermissionCode.USERS_SHOW);
        PermissionHelper.hideIfNotAllowed(btnBackup, PermissionCode.SETTINGS_BACKUP);
        // btnClose - متاح للجميع

        // إخفاء/إظهار TitledPane بناءً على محتوياتها
        applyTitledPaneVisibility();
    }

    /**
     * إخفاء TitledPane إذا كانت جميع أزرارها مخفية
     */
    private void applyTitledPaneVisibility() {
        checkAndHidePaneIfEmpty(paneSales);
        checkAndHidePaneIfEmpty(panePurchase);
        checkAndHidePaneIfEmpty(paneItems);
        checkAndHidePaneIfEmpty(paneCustom);
        checkAndHidePaneIfEmpty(paneSuppliers);
        checkAndHidePaneIfEmpty(paneTreasury);
        checkAndHidePaneIfEmpty(paneSetting);
    }

    /**
     * التحقق من وجود أزرار مرئية في الـ TitledPane
     */
    private void checkAndHidePaneIfEmpty(TitledPane pane) {
        if (pane.getContent() instanceof javafx.scene.layout.Pane contentPane) {
            boolean hasVisibleButton = contentPane.getChildren().stream()
                    .filter(node -> node instanceof Button)
                    .anyMatch(node -> node.isVisible());

            if (!hasVisibleButton) {
                pane.setVisible(false);
                pane.setManaged(false);
            }
        }
    }

    private void otherSetting() {
        var imageSetting = new Image_Setting();
        titlePaneSetting(paneSales, Setting_Language.WORD_SALES, imageSetting.shoppingSales);
        titlePaneSetting(panePurchase, Setting_Language.WORD_PUR, imageSetting.shoppingPurchase);
        titlePaneSetting(paneItems, Setting_Language.WORD_ITEMS, imageSetting.itemWhite);
        titlePaneSetting(paneCustom, Setting_Language.WORD_CUSTOM, imageSetting.personCustomer);
        titlePaneSetting(paneSuppliers, Setting_Language.WORD_SUP, imageSetting.personSup);
        titlePaneSetting(paneTreasury, Setting_Language.TREASURY, imageSetting.treasuryWhite);
        titlePaneSetting(paneSetting, Setting_Language.WORD_SETTING, imageSetting.setting);

        txtNameProject.setText(PROGRAM_TITLE);
        txtName.setText(PROGRAM_NAME_EN);
        txtTel.setText(PROGRAM_TEL);
    }


    private void titlePaneSetting(TitledPane titledPane, String text, InputStream stream) {
        titledPane.setText(text);
        titledPane.setGraphic(new ImageDesign(stream, 20));
    }

}
