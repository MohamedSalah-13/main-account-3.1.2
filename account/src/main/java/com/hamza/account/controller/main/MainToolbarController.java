package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.button.ImageDesign;
import com.jfoenix.controls.JFXHamburger;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

@Log4j2
@RequiredArgsConstructor
public class MainToolbarController implements Initializable {

    private final MainScreenController controller;
    @Getter
    @FXML
    private JFXHamburger hamburger;
    @FXML
    private Button btnAlarm;
    @FXML
    private Button btnCalc;
    @FXML
    private Button btnYouTube;
    @FXML
    private Button btnHome;
    @FXML
    private Button btnItems;
    @FXML
    private Button btnPurchase;
    @FXML
    private Button btnSales, btnPosSales;
    @FXML
    private Button btnShift;
    @FXML
    private ToolBar toolBar;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();

        // ✅ تطبيق الصلاحيات على أزرار الـ Toolbar
        applyPermissions();

        showButton(btnPosSales, false);
        showButton(btnAlarm, false);
        showButton(btnShift, false);
    }

    /**
     * ✅ تطبيق الصلاحيات على أزرار الـ Toolbar
     */
    private void applyPermissions() {
        // زر الرئيسية - متاح للجميع
        // btnHome - no permission needed

        // أزرار المبيعات
        PermissionHelper.hideIfNotAllowed(btnSales, PermissionCode.SALES_CREATE);
        PermissionHelper.hideIfNotAllowed(btnPosSales, PermissionCode.POS_SALE);

        // أزرار المشتريات
        PermissionHelper.hideIfNotAllowed(btnPurchase, PermissionCode.PURCHASE_CREATE);

        // أزرار الأصناف
        PermissionHelper.hideIfNotAllowed(btnItems, PermissionCode.ITEMS_SHOW);

        // أزرار الورديات
        if (PermissionHelper.has(PermissionCode.SHIFTS_OPEN) || PermissionHelper.has(PermissionCode.SHIFTS_CLOSE)) {
            showButton(btnShift, true);
        }

        // الآلة الحاسبة والمنبه - متاح للجميع
        // btnCalc, btnAlarm - no permission needed
    }

    private void showButton(Button button, boolean show) {
        button.setVisible(show);
        button.setManaged(show);
    }

    private void otherSetting() {

        MenuButtonSetting menuButtonSetting = new MenuButtonSetting(controller.getTabPane());
        var imageSetting = new Image_Setting();
        menuButtonSetting.configureButton(btnHome, imageSetting.homeWhite, controller.getSettingButtons().home());
        menuButtonSetting.configureButton(btnPosSales, imageSetting.shoppingSalesPOS, controller.getTotalSales().addInvoicePos());
        menuButtonSetting.configureButton(btnSales, imageSetting.shoppingSales, controller.getTotalSales().addInvoice());
        menuButtonSetting.configureButton(btnPurchase, imageSetting.shoppingPurchase, controller.getTotalPurchase().addInvoice());
        menuButtonSetting.configureButton(btnItems, imageSetting.itemWhite, controller.getItemsButtons().allItems());
        menuButtonSetting.configureButton(btnCalc, imageSetting.calcWhite, controller.getForAllButtons().calc());
        menuButtonSetting.configureButton(btnAlarm, imageSetting.alarmWhite, controller.getForAllButtons().alarm());
        menuButtonSetting.configureButton(btnShift, imageSetting.tools, controller.getShiftButtons().openShiftScreen());

        btnYouTube.setGraphic(new ImageDesign(imageSetting.youtube, 20));
        btnYouTube.setText("شرح البرنامج");
        btnYouTube.setTooltip(new javafx.scene.control.Tooltip("قناة يوتيوب - شرح البرنامج"));
        btnYouTube.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.youtube.com/playlist?list=PL2fs9t9FGXhoSOJ5UFsAWm2tLS_EfOvAE"));
            } catch (Exception ex) {
                log.error("Error opening YouTube link", ex);
            }
        });

        showButton(btnYouTube, LogApplication.usersVo.getId() == 1);

    }

}