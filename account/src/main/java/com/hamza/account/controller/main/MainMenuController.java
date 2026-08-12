package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import lombok.Getter;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Getter
public class MainMenuController {

    @FXML
    private Menu menuSales, menuPurchase, menuCustomer, menuSupplier, menuEmployees, menuSetting, menuItems, menuReport;

    @FXML
    private MenuItem menuItemSales, menuItemSalesReturn, menuItemTotalSales, menuItemTotalSalesReturn;
    @FXML
    private MenuItem menuItemPurchase, menuItemPurchaseReturn, menuItemTotalPurchase, menuItemTotalPurchaseReturn;
    @FXML
    private MenuItem menuItemItems, menuItemAddItem, menuItemAddItemFromExcel, menuItemUnit, menuItemArea, menuItemInventory, menuItemStockCount, menuItemMainGroup, menuItemSupGroup;
    @FXML
    private MenuItem menuItemAddCustomName, menuItemCustomName, menuItemCustomAccount;
    @FXML
    private MenuItem menuItemAddSupplierName, menuItemSuppliersName, menuItemSuppliersAccount;
    @FXML
    private MenuItem menuItemSummary, menuItemReportItems,
            menuItemReportItemsDaily, menuItemReportCustom, menuItemCustomPaid, menuItemReportSuppliers, menuItemSuppliersPaid, menuItemReportSales, menuItemReportPurchase, menuItemReportDetails, menuItemReportYearly, menuItemReportProfitLoss;
    @FXML
    private MenuItem menuItemAddUser, menuItemUsers, menuItemAddEmployee, menuItemEmployees;

    @FXML
    private MenuItem menuItemHome, menuItemSettingUsers, menuItemDeleteData, menuItemBackup, menuItemAbout, menuItemClose;
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
        menuItemShiftReports.setVisible(false);

        menuItemReportCustom.setVisible(false);
        menuItemReportSuppliers.setVisible(false);
    }

    private void otherSetting() {

        menuSales.setText(Setting_Language.WORD_SALES);
        menuPurchase.setText(Setting_Language.WORD_PUR);
        menuItems.setText(Setting_Language.WORD_ITEMS);
        menuCustomer.setText(Setting_Language.WORD_CUSTOM);
        menuSupplier.setText(Setting_Language.WORD_SUP);
        menuSetting.setText(Setting_Language.WORD_SETTING);
        menuEmployees.setText(Setting_Language.EMPLOYEES);
        menuReport.setText(Setting_Language.WORD_REPORT);
    }


    private void setGraphicMenuItems() {
        var images = new Image_Setting();
        menuItemHome.setGraphic(createIcon(images.homeWhite));
        menuItemSettingUsers.setGraphic(createIcon(images.setting));
        menuItemDeleteData.setGraphic(createIcon(images.cancel));
        menuItemBackup.setGraphic(createIcon(images.database));

        menuItemClose.setGraphic(createIcon(images.exit));
        menuItemAbout.setGraphic(createIcon(images.about));
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
//        menuEmployees.setGraphic(createIcon(images.personCustomer));
    }
}
