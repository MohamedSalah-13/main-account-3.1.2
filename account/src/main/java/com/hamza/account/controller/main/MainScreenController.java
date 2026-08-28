package com.hamza.account.controller.main;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.features.company.CompanyLogo;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.events.CompanyChanged;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.features.events.UserRenamed;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.model.domain.Company;
import com.hamza.account.model.domain.Users;
import com.hamza.account.view.MonthlyView;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
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
    private final Subscriptions subscriptions = new Subscriptions();
    public Pane mainPane;
    @FXML
    private BorderPane borderPane;
    @FXML
    private TabPane tabPane;
    @FXML
    private HBox mainContentBox;
    @FXML
    private VBox box;

    // ------------------------------------------------------------------
    // Right sidebar (formerly MainRightPaneController / mainRightPane-view.fxml)
    // ------------------------------------------------------------------
    private MenuButtonSetting menuButtonSetting;
    @FXML
    private AnchorPane rightPaneRoot;
    @FXML
    private Button btnSales, btnSalesReturn, btnTotalSale, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems,
            btnAddItem, btnUnits, btnMainGroup, btnSubGroup, btnArea, btnInventory, btnStockCount, btnStocks, btnStockTransfers, btnMergeItems,
            btnAddCustomerName, btnCustomer, btnAccountCustom, btnAddSupplierName, btnSuppliers,
            btnAccountSuppliers, btnAddEmployee, btnEmployees, btnAddUser, btnUsers,
            btnTreasuryDetails, btnProcess, btnExpenses,
            btnReportSummary, btnReportItems, btnReportItemsDaily, btnReportSalesByYear, btnReportPurchaseByYear,
            btnReportCustomPaid, btnReportSuppliersPaid, btnReportDetails, btnReportYearly, btnReportProfitLoss,
            btnReportReturnReasons,
            btnHome, btnSetting, btnShiftReports, btnBackup, btnDeleteData, btnAbout, btnClose;
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
    @FXML
    private MenuButton menuUser;
    @FXML
    private MenuItem menuItemChangeName, menuItemChangePass, menuItemLogout;
    @FXML
    private Label lblUserInitial, lblUserName, lblUserRole;

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
            tabPane.getTabs().getFirst().setText(LanguageManager.getInstance().getString("main"));
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
        configureAllButtons();
    }

    /**
     * Re-applies every piece of sidebar text that {@link #setupRightPane()} only
     * ever set once - the titled-pane headers, every nav button and menu item, the
     * "Main" tab title, the signed-in user's role label and the YouTube button -
     * on a {@link LanguageChanged} event. Nothing here rebuilds the tree or touches
     * click handlers (MenuButtonSetting.trackNavButton is idempotent for exactly
     * this reason); it only overwrites text that was frozen at first build.
     */
    private void refreshSidebarText() {
        try {
            rightPaneSetting();
            setupUser();
            setupYouTube();
            configureAllButtons();
            otherSetting();
        } catch (Exception e) {
            logException(e);
        }
    }

    private void configureAllButtons() throws Exception {
        var monthlyPurchaseInterface = new MonthlySalesInterface() {
            @Override
            public String reportName() {
                return "Annual_Purchase_Report";
            }

            @Override
            public String reportTitle() {
                return LanguageManager.getInstance().getString("report.monthly.purchase.title");
            }

            @Override
            public MonthlySalesViewDao getMonthlySalesViewDao(DaoFactory daoFactory) {
                return daoFactory.monthlyPurchaseViewDao();
            }

            @Override
            public String chartTitle() {
                return LanguageManager.getInstance().getString("report.monthly.purchase.chart.title");
            }

            @Override
            public boolean isPurchase() {
                return true;
            }
        };
        var monthlySalesInterface = new MonthlySalesInterface() {
        };

        /*----------------------------------------------- Sales -----------------------------------------------*/
        menuButtonSetting.configureButton(btnSales, getTotalSales().addInvoice());
        menuButtonSetting.configureButton(btnSalesReturn, getTotalSalesReturn().addInvoice());
        menuButtonSetting.configureButton(btnTotalSale, getTotalSales().totals());
        /*----------------------------------------------- Purchase -----------------------------------------------*/
        menuButtonSetting.configureButton(btnPurchase, getTotalPurchase().addInvoice());
        menuButtonSetting.configureButton(btnTotalPurchase, getTotalPurchase().totals());
        menuButtonSetting.configureButton(btnPurchaseRe, getTotalPurchaseReturn().addInvoice());
        menuButtonSetting.configureButton(btnTotalPurchaseRe, getTotalPurchaseReturn().totals());
        /*----------------------------------------------- Items -----------------------------------------------*/
        menuButtonSetting.configureButton(btnItems, getItemsButtons().allItems());
        menuButtonSetting.configureButton(btnAddItem, getItemsButtons().addItem());
        menuButtonSetting.configureButton(btnUnits, getItemsButtons().units());
        menuButtonSetting.configureButton(btnMainGroup, getItemsButtons().addMainGroup());
        menuButtonSetting.configureButton(btnSubGroup, getItemsButtons().addSubGroup());
        menuButtonSetting.configureButton(btnArea, getItemsButtons().areasList());
        menuButtonSetting.configureButton(btnInventory, getItemsButtons().inventory());
        menuButtonSetting.configureButton(btnStockCount, getItemsButtons().stockCount());
        menuButtonSetting.configureButton(btnStocks, getItemsButtons().stocks());
        menuButtonSetting.configureButton(btnStockTransfers, getItemsButtons().stockTransfers());
        menuButtonSetting.configureButton(btnMergeItems, getItemsButtons().mergeItems());
        /*----------------------------------------------- Custom -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddCustomerName, getNameCustomer().addName());
        menuButtonSetting.configureButton(btnCustomer, getNameCustomer().namesData());
        menuButtonSetting.configureButton(btnAccountCustom, getAccountButtonsCustom());
        /*----------------------------------------------- Suppliers -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddSupplierName, getNameSup().addName());
        menuButtonSetting.configureButton(btnSuppliers, getNameSup().namesData());
        menuButtonSetting.configureButton(btnAccountSuppliers, getAccountButtonsSup());
        /*----------------------------------------------- Employees -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddEmployee, getAddEmployee().addEmployee());
        menuButtonSetting.configureButton(btnEmployees, getAddEmployee().employees());
        menuButtonSetting.configureButton(btnAddUser, getUsersAll().getUsers_add());
        menuButtonSetting.configureButton(btnUsers, getUsersAll().getUsers_all());
        /*----------------------------------------------- Treasury -----------------------------------------------*/
        menuButtonSetting.configureButton(btnTreasuryDetails, getTreasuryButtons().treasuryDetails());
        menuButtonSetting.configureButton(btnProcess, getTreasuryButtons().openProcess());
        menuButtonSetting.configureButton(btnExpenses, getTreasuryButtons().openExpenses());
        /*----------------------------------------------- Reports -----------------------------------------------*/
        menuButtonSetting.configureButton(btnReportSummary, getReportsButtons().summaryReport());
        menuButtonSetting.configureButton(btnReportItems, getReportsButtons().itemsReport());
        menuButtonSetting.configureButton(btnReportItemsDaily, getReportsButtons().itemsReportDaily());
        menuButtonSetting.configureButton(btnReportSalesByYear, getAction(monthlySalesInterface.reportTitle(), monthlySalesInterface));
        menuButtonSetting.configureButton(btnReportPurchaseByYear, getAction(monthlyPurchaseInterface.reportTitle(), monthlyPurchaseInterface));
        menuButtonSetting.configureButton(btnReportCustomPaid, getReportsButtons().reportCustomPaid());
        menuButtonSetting.configureButton(btnReportSuppliersPaid, getReportsButtons().reportSupplierPaid());
        menuButtonSetting.configureButton(btnReportDetails, getReportsButtons().detailsReport());
        menuButtonSetting.configureButton(btnReportYearly, getReportsButtons().reportYearly());
        menuButtonSetting.configureButton(btnReportProfitLoss, getReportsButtons().profitLossReport());
        menuButtonSetting.configureButton(btnReportReturnReasons, getReportsButtons().returnReasonsReport());
        /*----------------------------------------------- Setting -----------------------------------------------*/
        menuButtonSetting.configureButton(btnHome, getSettingButtons().home());
        menuButtonSetting.configureButton(btnSetting, getSettingButtons().setting());
        menuButtonSetting.configureButton(btnShiftReports, getSettingButtons().adminShifts());
        menuButtonSetting.configureButton(btnBackup, getSettingButtons().backup());
        menuButtonSetting.configureButton(btnDeleteData, getSettingButtons().deleteData());
        menuButtonSetting.configureButton(btnAbout, getSettingButtons().about());
        menuButtonSetting.configureButton(btnClose, getSettingButtons().close());
        /*----------------------------------------------- User menu (moved from the removed top toolbar) -----------------------------------------------*/
        menuButtonSetting.initializeMenuItem(menuItemChangeName, getForAllButtons().changeName());
        menuButtonSetting.initializeMenuItem(menuItemChangePass, getForAllButtons().changePassword());
        menuButtonSetting.initializeMenuItem(menuItemLogout, getForAllButtons().logout());
        // Every signed-in user may log out, regardless of what other permissions they hold.
        menuItemLogout.setDisable(false);

        dontShowData();
    }

    private void rightPaneSetting() {
        var imageSetting = new Image_Setting();
        var lm = LanguageManager.getInstance();
        titlePaneSetting(paneSales, lm.getString("sales"), imageSetting.shoppingSales);
        titlePaneSetting(panePurchase, lm.getString("pur"), imageSetting.shoppingPurchase);
        titlePaneSetting(paneItems, lm.getString("items"), imageSetting.itemWhite);
        titlePaneSetting(paneCustom, lm.getString("customers"), imageSetting.personCustomer);
        titlePaneSetting(paneSuppliers, lm.getString("suppliers"), imageSetting.personSup);
        titlePaneSetting(paneEmployees, lm.getString("employees"), imageSetting.account);
        titlePaneSetting(paneTreasury, lm.getString("treasury.label.treasury"), imageSetting.treasuryWhite);
        titlePaneSetting(paneReports, lm.getString("report"), imageSetting.reports);
        titlePaneSetting(paneSetting, lm.getString("menu.settings"), imageSetting.setting);

        // Fixed bilingual brand identity, not translatable UI text - same choice
        // made for SettingApplication's stage title.
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
            subscriptions.add(eventBus.subscribe(LanguageChanged.class, event -> refreshSidebarText()));
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
        lblCompanyName.setText(name == null || name.isBlank() ? PROGRAM_TITLE : name);

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
        var lm = LanguageManager.getInstance();
        lblUserName.setText(username == null || username.isBlank() ? "-" : username);
        lblUserRole.setText(CurrentUser.get().getId() == 1 ? lm.getString("nav.user.role.admin") : lm.getString("nav.user.role.user"));
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
        btnYouTube.setText(LanguageManager.getInstance().getString("nav.youtube.explain"));
        btnYouTube.setTooltip(new Tooltip(LanguageManager.getInstance().getString("nav.youtube.tooltip")));
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
        return new ButtonWithPerm() {
            @Override
            public PermissionKey getPermissionType() {
                return monthlySalesInterface.isPurchase()
                        ? AppPermissions.REPORTS_SHOW_PURCHASE
                        : AppPermissions.REPORTS_SHOW_SALES;
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
        AllAlerts.handleError(LanguageManager.getInstance().getString("nav.error.open.screen"), e);
    }


    private void dontShowData() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(paneEmployees, AppPermissions.EMPLOYEE_SHOW);
        permissionDisableService.applyPermissionBasedDisable(paneSetting, AppPermissions.SETTING_SHOW);
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
