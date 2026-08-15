package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.company.CompanyLogo;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.events.CompanyChanged;
import com.hamza.account.features.events.UserRenamed;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
public class MainRightPaneController implements Initializable {

    @Getter
    @FXML
    private Button btnSales, btnSalesReturn, btnTotalSale, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems,
            btnAddItem, btnUnits, btnMainGroup, btnSubGroup, btnArea, btnInventory, btnStockCount,
            btnAddCustomerName, btnCustomer, btnAccountCustom, btnAddSupplierName, btnSuppliers,
            btnAccountSuppliers, btnAddEmployee, btnEmployees, btnAddUser, btnUsers,
            btnTreasuryDetails, btnProcess, btnExpenses,
            btnReportSummary, btnReportItems, btnReportItemsDaily, btnReportSalesByYear, btnReportPurchaseByYear,
            btnReportCustomPaid, btnReportSuppliersPaid, btnReportDetails, btnReportYearly, btnReportProfitLoss,
            btnHome, btnSetting, btnShiftReports, btnBackup, btnDeleteData, btnAbout, btnClose;
    @Getter
    @FXML
    private TitledPane paneEmployees, paneSetting;
    @FXML
    private TitledPane paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneReports;
    @FXML
    private Text txtNameProject, txtName, txtTel;
    @FXML
    private AnchorPane rootPane;
    @FXML
    private ImageView imgCompanyLogo;
    @FXML
    private Label lblCompanyName;
    @FXML
    private StackPane notificationBellSlot;
    @FXML
    private Button btnYouTube;
    @Getter
    @FXML
    private MenuButton menuUser;
    @Getter
    @FXML
    private MenuItem menuItemChangeName, menuItemChangePass, menuItemLogout;
    @FXML
    private Label lblUserInitial, lblUserName, lblUserRole;

    private final Subscriptions subscriptions = new Subscriptions();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        setupBrand();
        setupUser();
        setupNotificationBell();
        setupYouTube();
    }

    private void otherSetting() {
        var imageSetting = new Image_Setting();
        titlePaneSetting(paneSales, Setting_Language.WORD_SALES, imageSetting.shoppingSales);
        titlePaneSetting(panePurchase, Setting_Language.WORD_PUR, imageSetting.shoppingPurchase);
        titlePaneSetting(paneItems, Setting_Language.WORD_ITEMS, imageSetting.itemWhite);
        titlePaneSetting(paneCustom, Setting_Language.WORD_CUSTOM, imageSetting.personCustomer);
        titlePaneSetting(paneSuppliers, Setting_Language.WORD_SUP, imageSetting.personSup);
        titlePaneSetting(paneEmployees, Setting_Language.EMPLOYEES, imageSetting.account);
        titlePaneSetting(paneTreasury, Setting_Language.TREASURY, imageSetting.treasuryWhite);
        titlePaneSetting(paneReports, Setting_Language.WORD_REPORT, imageSetting.reports);
        titlePaneSetting(paneSetting, Setting_Language.WORD_SETTING, imageSetting.setting);

        txtNameProject.setText(PROGRAM_TITLE);
        txtName.setText(PROGRAM_NAME_EN);
        txtTel.setText(PROGRAM_TEL);
    }

    private void titlePaneSetting(TitledPane titledPane, String text, InputStream stream) {
        titledPane.setText(text);
        titledPane.setGraphic(new ImageDesign(stream, 20));
    }

    // ------------------------------------------------------------------
    // Company brand (logo + name)
    // ------------------------------------------------------------------

    private void setupBrand() {
        loadCompanyBrand();

        EventBus eventBus = ServiceRegistry.get(EventBus.class);
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(CompanyChanged.class, event -> loadCompanyBrand()));
            subscriptions.add(eventBus.subscribe(UserRenamed.class, event -> lblUserName.setText(event.name())));
            subscriptions.disposeWith(rootPane);
        }
    }

    private void loadCompanyBrand() {
        Thread thread = new Thread(() -> {
            try {
                Company company = new CompanyService(DaoFactory.INSTANCE).load();
                Platform.runLater(() -> applyCompany(company));
            } catch (Exception e) {
                log.error("Failed to load company for sidebar branding", e);
            }
        }, "sidebar-company-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyCompany(Company company) {
        String name = company.getName();
        lblCompanyName.setText(name == null || name.isBlank() ? Setting_Language.PROGRAM_TITLE : name);

        CompanyLogo logo = CompanyLogo.fromStored(company.getImage());
        Image image = logo == null ? new Image(new Image_Setting().defaultBlog) : logo.toFxImage();
        imgCompanyLogo.setImage(image);
    }

    // ------------------------------------------------------------------
    // Current user menu (change name / change password / logout)
    // ------------------------------------------------------------------

    private void setupUser() {
        Users user = CurrentUser.getOrNull();
        if (user == null) {
            menuUser.setVisible(false);
            menuUser.setManaged(false);
            return;
        }
        String username = user.getUsername();
        lblUserName.setText(username == null || username.isBlank() ? "-" : username);
        lblUserRole.setText(CurrentUser.get().getId() == 1 ? "مدير النظام" : "مستخدم");
        lblUserInitial.setText(username == null || username.isBlank() ? "?" : username.substring(0, 1).toUpperCase());
    }

    // ------------------------------------------------------------------
    // Notifications + YouTube (moved here from the removed top toolbar)
    // ------------------------------------------------------------------

    private void setupNotificationBell() {
        notificationBellSlot.getChildren().setAll(NotificationBootstrap.start().createBell());
    }

    private void setupYouTube() {
        var imageSetting = new Image_Setting();
        btnYouTube.setGraphic(new ImageDesign(imageSetting.youtube, 20));
        btnYouTube.setText("شرح البرنامج");
        btnYouTube.setTooltip(new Tooltip("قناة يوتيوب - شرح البرنامج"));
        btnYouTube.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new URI("https://www.youtube.com/playlist?list=PL2fs9t9FGXhoSOJ5UFsAWm2tLS_EfOvAE"));
            } catch (Exception ex) {
                log.error("Error opening YouTube link", ex);
            }
        });

        boolean isAdmin = CurrentUser.getOrNull() != null && CurrentUser.get().getId() == 1;
        btnYouTube.setVisible(isAdmin);
        btnYouTube.setManaged(isAdmin);
    }
}
