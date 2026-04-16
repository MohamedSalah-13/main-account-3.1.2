package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import com.jfoenix.controls.JFXHamburger;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

@RequiredArgsConstructor
public class MainToolbarController implements Initializable {

    private final MainScreenController controller;
    private final DataPublisher dataPublisher;
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
    private MenuItem menuItemChangeName, menuItemChangePass, menuItemLogout;
    @FXML
    private MenuButton menuButton;
    @FXML
    private ToolBar toolBar;
    private String nameProperty;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        dataPublisher.getPublisherAddUser().addObserver(message -> menuButton.setText(Setting_Language.WELCOME + " " + message + " !"));
        dataPublisher.getShowLoginScreen().addObserver(message -> menuItemLogout.setDisable(!message));
        toolBar.getItems().remove(btnAlarm);
//        toolBar.getItems().remove(btnPosSales);
    }

    private void otherSetting() {

        MenuButtonSetting menuButtonSetting = new MenuButtonSetting(controller.getTabPane());
        var imageSetting = new Image_Setting();
        menuButtonSetting.configureButton(btnHome, imageSetting.homeWhite, controller.getSettingButtons().home());
        menuButtonSetting.configureButton(btnPosSales, imageSetting.shoppingSalesPOS, controller.getTotalSales().addInvoicePos());
        menuButtonSetting.configureButton(btnSales, imageSetting.shoppingSales, controller.getTotalSales().addInvoice());
        menuButtonSetting.configureButton(btnPurchase, imageSetting.shoppingPurchase, controller.getTotalPurchase().addInvoice());
        menuButtonSetting.configureButton(btnItems, imageSetting.itemWhite, controller.getItemsButtons().allItems(controller));
        menuButtonSetting.configureButton(btnCalc, imageSetting.calcWhite, controller.getForAllButtons().calc());
        menuButtonSetting.configureButton(btnAlarm, imageSetting.alarmWhite, controller.getForAllButtons().alarm());
        menuButtonSetting.configureButton(btnShift, imageSetting.tools, controller.getShiftButtons().openShiftScreen());

        btnYouTube.setGraphic(new ImageDesign(imageSetting.youtube, 36));
        btnYouTube.setText("شرح البرنامج");
        btnYouTube.setTooltip(new javafx.scene.control.Tooltip("قناة يوتيوب - شرح البرنامج"));
        btnYouTube.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.youtube.com/playlist?list=PL2fs9t9FGXhoSOJ5UFsAWm2tLS_EfOvAE"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        menuButton.setGraphic(new ImageDesign(imageSetting.personCustomer, 36));
        menuButton.setText(Setting_Language.WELCOME + " " + nameProperty + " !");
        menuButtonSetting.initializeMenuItem(menuItemChangeName, controller.getForAllButtons().changeName());
        menuButtonSetting.initializeMenuItem(menuItemChangePass, controller.getForAllButtons().changePassword());
        menuButtonSetting.initializeMenuItem(menuItemLogout, controller.getForAllButtons().logout());
        menuItemLogout.setDisable(!PropertiesName.getSettingLoginShow());

    }

}
