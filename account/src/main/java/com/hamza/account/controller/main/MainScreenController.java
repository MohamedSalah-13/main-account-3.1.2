package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.features.company.CompanyLogo;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.events.CompanyChanged;
import com.hamza.account.features.events.UserRenamed;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.domain.Company;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.MonthlyView;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPathImageMainScreen;
import static com.hamza.account.config.PropertiesName.getShowMainTotals;
import static com.hamza.controlsfx.language.Setting_Language.*;

public class MainScreenController extends MainItems implements Initializable {

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ContextMenu slideshowMenu = new ContextMenu();
    public Pane mainPane;
    @FXML
    private BorderPane borderPane;
    @Getter
    @FXML
    private TabPane tabPane;
    @FXML
    private HBox mainContentBox;
    @FXML
    private VBox box;

    private MenuButtonSetting menuButtonSetting;

    // ------------------------------------------------------------------
    // Right sidebar (formerly MainRightPaneController / mainRightPane-view.fxml)
    // ------------------------------------------------------------------

    @FXML
    private AnchorPane rightPaneRoot;
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

    public MainScreenController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        menuButtonSetting = new MenuButtonSetting(tabPane);
        otherSetting();
        addTabContextMenu();

        if (AuthorizationGuard.isGranted(AppPermissions.REPORTS_SHOW_SUMMARY)) {
            if (getShowMainTotals()) firstBoxInMain();
        }

        // data publisher
        var name = CurrentUser.get().getUsername();
        if (eventBus != null) eventBus.publish(new UserRenamed(name));
        // This controller is the publisher bag it subscribes to, so there is nothing
        // that could outlive the observers registered here.
        getChangeMainScreenImage().addObserver(message -> setBackgroundImage());
        getShowMainTotalsScreen().addObserver(message -> {
            if (message == true) {
                firstBoxInMain();
            } else {
                box.getChildren().clear();
            }
        });

        try {
            setupRightPane();
        } catch (Exception e) {
            logException(e);
        }
    }

    private void otherSetting() {
        try {
            tabPane.getTabs().getFirst().setText(Setting_Language.WORD_MAIN);
            tabPane.getTabs().getFirst().setClosable(false);
        } catch (Exception e) {
            logException(e);
        }
    }

    private void setupRightPane() throws Exception {
        rightPaneSetting();
        setupBrand();
        setupUser();
        setupNotificationBell();
        setupYouTube();

        var monthlyPurchaseInterface = new MonthlySalesInterface() {
            @Override
            public String reportName() {
                return "تقرير المشتريات السنوي";
            }

            @Override
            public String reportTitle() {
                return "تقرير إجمالي المشتريات الشهرية لكل سنة";
            }

            @Override
            public MonthlySalesViewDao getMonthlySalesViewDao(DaoFactory daoFactory) {
                return daoFactory.monthlyPurchaseViewDao();
            }

            @Override
            public String chartTitle() {
                return "مقارنة المشتريات بين الشهور";
            }
        };
        var monthlySalesInterface = new MonthlySalesInterface() {
        };

        /*----------------------------------------------- Sales -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnSales(), getTotalSales().addInvoice());
        menuButtonSetting.configureButton(getBtnSalesReturn(), getTotalSalesReturn().addInvoice());
        menuButtonSetting.configureButton(getBtnTotalSale(), getTotalSales().totals());
        /*----------------------------------------------- Purchase -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnPurchase(), getTotalPurchase().addInvoice());
        menuButtonSetting.configureButton(getBtnTotalPurchase(), getTotalPurchase().totals());
        menuButtonSetting.configureButton(getBtnPurchaseRe(), getTotalPurchaseReturn().addInvoice());
        menuButtonSetting.configureButton(getBtnTotalPurchaseRe(), getTotalPurchaseReturn().totals());
        /*----------------------------------------------- Items -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnItems(), getItemsButtons().allItems());
        menuButtonSetting.configureButton(getBtnAddItem(), getItemsButtons().addItem());
        menuButtonSetting.configureButton(getBtnUnits(), getItemsButtons().units());
        menuButtonSetting.configureButton(getBtnMainGroup(), getItemsButtons().addMainGroup());
        menuButtonSetting.configureButton(getBtnSubGroup(), getItemsButtons().addSubGroup());
        menuButtonSetting.configureButton(getBtnArea(), getItemsButtons().areasList());
        menuButtonSetting.configureButton(getBtnInventory(), getItemsButtons().inventory());
        menuButtonSetting.configureButton(getBtnStockCount(), getItemsButtons().stockCount());
        /*----------------------------------------------- Custom -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnAddCustomerName(), getNameCustomer().addName());
        menuButtonSetting.configureButton(getBtnCustomer(), getNameCustomer().namesData());
        menuButtonSetting.configureButton(getBtnAccountCustom(), getAccountButtonsCustom());
        /*----------------------------------------------- Suppliers -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnAddSupplierName(), getNameSup().addName());
        menuButtonSetting.configureButton(getBtnSuppliers(), getNameSup().namesData());
        menuButtonSetting.configureButton(getBtnAccountSuppliers(), getAccountButtonsSup());
        /*----------------------------------------------- Employees -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnAddEmployee(), getAddEmployee().addEmployee());
        menuButtonSetting.configureButton(getBtnEmployees(), getAddEmployee().employees());
        menuButtonSetting.configureButton(getBtnAddUser(), getUsersAll().getUsers_add());
        menuButtonSetting.configureButton(getBtnUsers(), getUsersAll().getUsers_all());
        /*----------------------------------------------- Treasury -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnTreasuryDetails(), getTreasuryButtons().treasuryDetails());
        menuButtonSetting.configureButton(getBtnProcess(), getTreasuryButtons().openProcess());
        menuButtonSetting.configureButton(getBtnExpenses(), getTreasuryButtons().openExpenses());
        /*----------------------------------------------- Reports -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnReportSummary(), getReportsButtons().summaryReport());
        menuButtonSetting.configureButton(getBtnReportItems(), getReportsButtons().itemsReport());
        menuButtonSetting.configureButton(getBtnReportItemsDaily(), getReportsButtons().itemsReportDaily());
        menuButtonSetting.configureButton(getBtnReportSalesByYear(), getAction(monthlySalesInterface.reportName(), monthlySalesInterface));
        menuButtonSetting.configureButton(getBtnReportPurchaseByYear(), getAction(monthlyPurchaseInterface.reportName(), monthlyPurchaseInterface));
        menuButtonSetting.configureButton(getBtnReportCustomPaid(), getReportsButtons().reportCustomPaid());
        menuButtonSetting.configureButton(getBtnReportSuppliersPaid(), getReportsButtons().reportSupplierPaid());
        menuButtonSetting.configureButton(getBtnReportDetails(), getReportsButtons().detailsReport());
        menuButtonSetting.configureButton(getBtnReportYearly(), getReportsButtons().reportYearly());
        menuButtonSetting.configureButton(getBtnReportProfitLoss(), getReportsButtons().profitLossReport());
        /*----------------------------------------------- Setting -----------------------------------------------*/
        menuButtonSetting.configureButton(getBtnHome(), getSettingButtons().home());
        menuButtonSetting.configureButton(getBtnSetting(), getSettingButtons().setting());
        menuButtonSetting.configureButton(getBtnShiftReports(), getSettingButtons().adminShifts());
        menuButtonSetting.configureButton(getBtnBackup(), getSettingButtons().backup());
        menuButtonSetting.configureButton(getBtnDeleteData(), getSettingButtons().deleteData());
        menuButtonSetting.configureButton(getBtnAbout(), getSettingButtons().about());
        menuButtonSetting.configureButton(getBtnClose(), getSettingButtons().close());
        /*----------------------------------------------- User menu (moved from the removed top toolbar) -----------------------------------------------*/
        menuButtonSetting.initializeMenuItem(getMenuItemChangeName(), getForAllButtons().changeName());
        menuButtonSetting.initializeMenuItem(getMenuItemChangePass(), getForAllButtons().changePassword());
        menuButtonSetting.initializeMenuItem(getMenuItemLogout(), getForAllButtons().logout());
        // Every signed-in user may log out, regardless of what other permissions they hold.
        getMenuItemLogout().setDisable(false);

        dontShowData();
    }

    private void rightPaneSetting() {
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

        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(CompanyChanged.class, event -> loadCompanyBrand()));
            subscriptions.add(eventBus.subscribe(UserRenamed.class, event -> lblUserName.setText(event.name())));
            subscriptions.disposeWith(rightPaneRoot);
        }
    }

    private void loadCompanyBrand() {
        Thread thread = new Thread(() -> {
            try {
                Company company = new CompanyService(DaoFactory.INSTANCE).load();
                Platform.runLater(() -> applyCompany(company));
            } catch (Exception e) {
                logException(e);
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
                logException(ex);
            }
        });

        boolean isAdmin = CurrentUser.getOrNull() != null && CurrentUser.get().getId() == 1;
        btnYouTube.setVisible(isAdmin);
        btnYouTube.setManaged(isAdmin);
    }

    private ButtonWithPerm getAction(String name, MonthlySalesInterface monthlySalesInterface) {
        String sales = "مبيعات";
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                if (name.contains(sales))
                    return AppPermissions.REPORTS_SHOW_SALES;
                else return AppPermissions.REPORTS_SHOW_PURCHASE;
            }

            @Override
            public void action() throws Exception {
                new MonthlyView(daoFactory, monthlySalesInterface).start(new Stage());
            }

            @NotNull
            @Override
            public String textName() {
                return name;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
            }

        };
    }

    private void firstBoxInMain() {
        try {
            box.getChildren().clear();
            box.getChildren().add(new ModernDashboardApp(daoFactory, this).getPane());
        } catch (DaoException e) {
            logException(e);
        }
    }

    private void setBackgroundImage() {
        try {
            String imagePath = getPathImageMainScreen();
            if (imagePath.isEmpty()) {
                mainPane.setBackground(null);
                return;
            }
            ImageView backgroundImage = new ImageView(new Image(new FileInputStream(imagePath)));
            backgroundImage.setPreserveRatio(false);
            backgroundImage.fitWidthProperty().bind(mainPane.widthProperty());
            backgroundImage.fitHeightProperty().bind(mainPane.heightProperty());
            mainPane.setBackground(new Background(new BackgroundImage(
                    backgroundImage.getImage(),
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(1.0, 1.0, true, true, false, true)
            )));


        } catch (Exception e) {
            logException(e);
        }
    }

    private void logException(Exception e) {
        AllAlerts.handleError("فتح شاشة من القائمة الرئيسية", e);
    }


    private void dontShowData() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(getPaneEmployees(), AppPermissions.EMPLOYEE_SHOW);
        permissionDisableService.applyPermissionBasedDisable(getPaneSetting(), AppPermissions.SETTING_SHOW);
    }


    private void addTabContextMenu() {
        tabPane.getTabs().forEach(this::addContextMenuToTab);
        tabPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(this::addContextMenuToTab);
                }
            }
        });
    }

    private void addContextMenuToTab(Tab tab) {
        if (tab == tabPane.getTabs().getFirst()) return;

        ContextMenu contextMenu = new ContextMenu();
        MenuItem closeItem = new MenuItem("Close");
        MenuItem closeAllItem = new MenuItem("Close all tabs");
        MenuItem closeAllRightItem = new MenuItem("Close all right tabs");
        MenuItem closeAllLeftItem = new MenuItem("Close all left tabs");
        MenuItem closeOtherItem = new MenuItem("Close other tabs");


        closeItem.setOnAction(e -> tabPane.getTabs().remove(tab));
        closeAllItem.setOnAction(e -> {
            var tabs = new ArrayList<>(tabPane.getTabs());
            tabs.stream()
                    .filter(t -> t != tabPane.getTabs().getFirst())
                    .forEach(t -> tabPane.getTabs().remove(t));
        });

        closeAllRightItem.setOnAction(e -> {
            int currentIndex = tabPane.getTabs().indexOf(tab);
            var tabs = new ArrayList<>(tabPane.getTabs());
            tabs.stream()
                    .filter(t -> tabPane.getTabs().indexOf(t) > currentIndex)
                    .forEach(t -> tabPane.getTabs().remove(t));
        });

        closeAllLeftItem.setOnAction(e -> {
            int currentIndex = tabPane.getTabs().indexOf(tab);
            var tabs = new ArrayList<>(tabPane.getTabs());
            tabs.stream()
                    .filter(t -> tabPane.getTabs().indexOf(t) < currentIndex && t != tabPane.getTabs().getFirst())
                    .forEach(t -> tabPane.getTabs().remove(t));
        });

        closeOtherItem.setOnAction(e -> {
            var tabs = new ArrayList<>(tabPane.getTabs());
            tabs.stream()
                    .filter(t -> t != tab && t != tabPane.getTabs().getFirst())
                    .forEach(t -> tabPane.getTabs().remove(t));
        });

        contextMenu.getItems().addAll(closeItem, closeAllItem, closeAllRightItem, closeAllLeftItem, closeOtherItem);
        tab.setContextMenu(contextMenu);

    }
}
