package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.setting.FontColorDialog;
import com.hamza.account.view.NumberGenerator;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import lombok.Getter;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

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
    private MenuItem menuItemItems, menuItemAddItem, menuItemAddItemFromExcel, menuItemUnit, menuItemArea, menuItemInventory, menuItemMiniQuantity, menuItemMainGroup, menuItemSupGroup, menuItemConvertStock;
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
//            log.error("Error opening font and color dialog", e);
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
//        menuEmployees.setGraphic(createIcon(images.personCustomer));
    }
}
