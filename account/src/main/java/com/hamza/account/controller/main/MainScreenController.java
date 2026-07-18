package com.hamza.account.controller.main;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.MonthlyView;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPathImageMainScreen;
import static com.hamza.account.config.PropertiesName.getShowMainTotals;
import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
public class MainScreenController extends MainItems implements Initializable {

    private final Publisher<String> publisherAddUser;
    private final ContextMenu slideshowMenu = new ContextMenu();
    public Pane mainPane;
    private MainMenuController menuController;
    @FXML
    private MenuBar menuBar;
    @FXML
    private BorderPane borderPane;
    @Getter
    @FXML
    private TabPane tabPane;
    @FXML
    private VBox box;
    private String nameProperty;
    @FXML
    private Button btnSales, btnTotalSale, btnSalesRe, btnTotalSalesRe;
    @FXML
    private Button btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe;
    @FXML
    private Button btnItems, btnAddItem, btnItemFromExcel, btnUnits, btnMainGroup, btnSupGroup, btnInventory, btnConvertStock;
    @FXML
    private Button btnAddCustomer, btnCustomer, btnAccountCustom, btnAddSuppliers, btnSuppliers, btnAccountSuppliers;
    @FXML
    private Button btnAddUsers, btnUsers, btnAddEmployee, btnEmployees,
            btnTreasuryDetails, btnConvertTreasury, btnProcess, btnExpenses;
    @FXML
    private Button btnItemSummary, btnReportItems, btnReportItemsDaily, btnReportSalesByYear, btnReportPurchaseByYear, btnCustomPaid, btnSuppliersPaid, btnReportDetails, btnReportDelegate, btnReportYearly, btnReportProfitLoss;
    @FXML
    private Button btnHome, btnSetting, btnDeleteData, btnBackup, btnAbout, btnClose;
    @FXML
    private TitledPane paneSales, panePurchase, paneItems, paneCustom, paneSuppliers, paneTreasury, paneReports, paneSetting;
    @FXML
    private Text txtNameProject, txtName, txtTel;
    @FXML
    private MenuItem menuItemChangeName, menuItemChangePass, menuItemLogout;
    @FXML
    private MenuButton menuButton;
    @FXML
    private Button btnYouTube;

    private BackgroundSlideshow slideshow;

    public MainScreenController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.publisherAddUser = getPublisherAddUser();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        dontShowData();
        tabPane.getTabs().getFirst().setText(Setting_Language.WORD_MAIN);
        tabPane.getTabs().getFirst().setClosable(false);
        addTabContextMenu();
        getRightPane();
        applyPermissions();
        addYoutube();

        if (LogApplication.usersVo.getId() == 1) {
            if (getShowMainTotals()) firstBoxInMain();
        }

        // data publisher
        var name = LogApplication.usersVo.getUsername();
        publisherAddUser.setAvailability(name);
        getChangeMainScreenImage().addObserver(message -> setBackgroundImage());
        getShowMainTotalsScreen().addObserver(message -> {
            if (message == true) {
                firstBoxInMain();
            } else {
                box.getChildren().clear();
            }
        });

    }

    private void addYoutube() {
        var imageSetting = new Image_Setting();
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

    private void showButton(Button button, boolean show) {
        button.setVisible(show);
        button.setManaged(show);
    }

    private void getRightPane() {
        try {

            this.getPublisherAddUser().addObserver(message -> menuButton.setText(Setting_Language.WELCOME + " " + message + " !"));
            this.getShowLoginScreen().addObserver(message -> menuItemLogout.setDisable(!message));

            MenuButtonSetting menuButtonSetting = new MenuButtonSetting(tabPane);
            menuButton.setText(Setting_Language.WELCOME + " " + nameProperty + " !");
            menuButtonSetting.initializeMenuItem(menuItemChangeName, getForAllButtons().changeName());
            menuButtonSetting.initializeMenuItem(menuItemChangePass, getForAllButtons().changePassword());
            menuButtonSetting.initializeMenuItem(menuItemLogout, getForAllButtons().logout());
            menuItemLogout.setDisable(!PropertiesName.getSettingLoginShow());


            /*----------------------------------------------- Sales -----------------------------------------------*/
            menuButtonSetting.configureButton(btnSales, getTotalSales().addInvoice());
            menuButtonSetting.configureButton(btnTotalSale, getTotalSales().totals());
            menuButtonSetting.configureButton(btnSalesRe, getTotalSalesReturn().addInvoice());
            menuButtonSetting.configureButton(btnTotalSalesRe, getTotalSalesReturn().totals());
            /*----------------------------------------------- Purchase -----------------------------------------------*/
            menuButtonSetting.configureButton(btnPurchase, getTotalPurchase().addInvoice());
            menuButtonSetting.configureButton(btnTotalPurchase, getTotalPurchase().totals());
            menuButtonSetting.configureButton(btnPurchaseRe, getTotalPurchaseReturn().addInvoice());
            menuButtonSetting.configureButton(btnTotalPurchaseRe, getTotalPurchaseReturn().totals());
            /*----------------------------------------------- Items -----------------------------------------------*/
            menuButtonSetting.configureButton(btnAddItem, getItemsButtons().addItem());
            menuButtonSetting.configureButton(btnItems, getItemsButtons().allItems());
            menuButtonSetting.configureButton(btnItemFromExcel, getItemsButtons().addItemsFromExcel());
            menuButtonSetting.configureButton(btnUnits, getItemsButtons().units());
            menuButtonSetting.configureButton(btnInventory, getItemsButtons().inventory());
            menuButtonSetting.configureButton(btnMainGroup, getItemsButtons().addMainGroup());
            menuButtonSetting.configureButton(btnSupGroup, getItemsButtons().addSubGroup());
            menuButtonSetting.configureButton(btnConvertStock, getItemsButtons().convertStock());
            /*----------------------------------------------- Custom -----------------------------------------------*/
            menuButtonSetting.configureButton(btnAddCustomer, getNameCustomer().addName());
            menuButtonSetting.configureButton(btnCustomer, getNameCustomer().namesData());
            menuButtonSetting.configureButton(btnAccountCustom, getAccountButtonsCustom());
            /*----------------------------------------------- Suppliers -----------------------------------------------*/
            menuButtonSetting.configureButton(btnAddSuppliers, getNameSup().addName());
            menuButtonSetting.configureButton(btnSuppliers, getNameSup().namesData());
            menuButtonSetting.configureButton(btnAccountSuppliers, getAccountButtonsSup());
            /*----------------------------------------------- Employees -----------------------------------------------*/
            menuButtonSetting.configureButton(btnAddUsers, getUsersAll().getUsers_add());
            menuButtonSetting.configureButton(btnUsers, getUsersAll().getUsers_all());
            menuButtonSetting.configureButton(btnAddEmployee, getAddEmployee().addEmployee());
            menuButtonSetting.configureButton(btnEmployees, getAddEmployee().employees());

            menuButtonSetting.configureButton(btnTreasuryDetails, getTreasuryButtons().treasuryDetails());
            menuButtonSetting.configureButton(btnConvertTreasury, getTreasuryButtons().convertTreasury());
            menuButtonSetting.configureButton(btnProcess, getTreasuryButtons().openProcess());
            menuButtonSetting.configureButton(btnExpenses, getTreasuryButtons().openExpenses());
            /*----------------------------------------------- Reports -----------------------------------------------*/
            menuButtonSetting.configureButton(btnItemSummary, getReportsButtons().summaryReport());
            menuButtonSetting.configureButton(btnReportItems, getReportsButtons().itemsReport());
            menuButtonSetting.configureButton(btnReportItemsDaily, getReportsButtons().itemsReportDaily());

            var monthlySalesInterface = new MonthlySalesInterface() {
            };

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
//
//
            menuButtonSetting.configureButton(btnReportSalesByYear, getAction(monthlySalesInterface.reportName(), monthlySalesInterface));
            menuButtonSetting.configureButton(btnReportPurchaseByYear, getAction(monthlyPurchaseInterface.reportName(), monthlyPurchaseInterface));
            menuButtonSetting.configureButton(btnCustomPaid, getReportsButtons().reportCustomPaid());
            menuButtonSetting.configureButton(btnSuppliersPaid, getReportsButtons().reportSupplierPaid());
            menuButtonSetting.configureButton(btnReportDetails, getReportsButtons().detailsReport());
            menuButtonSetting.configureButton(btnReportDelegate, getReportsButtons().delegateReport());
            menuButtonSetting.configureButton(btnReportYearly, getReportsButtons().reportYearly());
            menuButtonSetting.configureButton(btnReportProfitLoss, getReportsButtons().profitLossReport());

            /*----------------------------------------------- Setting -----------------------------------------------*/
            menuButtonSetting.configureButton(btnHome, getSettingButtons().home());
            menuButtonSetting.configureButton(btnSetting, getSettingButtons().setting());
            menuButtonSetting.configureButton(btnDeleteData, getSettingButtons().deleteData());
            menuButtonSetting.configureButton(btnBackup, getSettingButtons().backup());
            menuButtonSetting.configureButton(btnAbout, getSettingButtons().about());
            menuButtonSetting.configureButton(btnClose, getSettingButtons().close());


            //--------------------------------------------------------------------------------
            var imageSetting = new Image_Setting();
            titlePaneSetting(paneSales, Setting_Language.WORD_SALES, imageSetting.shoppingSales);
            titlePaneSetting(panePurchase, Setting_Language.WORD_PUR, imageSetting.shoppingPurchase);
            titlePaneSetting(paneItems, Setting_Language.WORD_ITEMS, imageSetting.itemWhite);
            titlePaneSetting(paneCustom, Setting_Language.WORD_CUSTOM, imageSetting.personCustomer);
            titlePaneSetting(paneSuppliers, Setting_Language.WORD_SUP, imageSetting.personSup);
            titlePaneSetting(paneTreasury, Setting_Language.TREASURY, imageSetting.treasuryWhite);
            titlePaneSetting(paneReports, WORD_REPORT, imageSetting.reports);
            titlePaneSetting(paneSetting, Setting_Language.WORD_SETTING, imageSetting.setting);

            txtNameProject.setText(PROGRAM_TITLE);
            txtName.setText(PROGRAM_NAME_EN);
            txtTel.setText(PROGRAM_TEL);
        } catch (Exception e) {
            logException(e);
        }
    }


//    private void menuBarSetting() {
//        try {
//            FXMLLoader fxmlLoader = new FxmlConstants().menuBar;
//            MenuBar paneMenuBar = fxmlLoader.load();
//            // add to border pane
//            borderPane.setTop(paneMenuBar);
//            menuController = fxmlLoader.getController();
//            sales(menuController);
//            purchase(menuController);
//            initializeMainMenuItems(menuController);
//            customers(menuController);
//            suppliers(menuController);
//            employees(menuController);
//            menuButtonSetting.initializeMenuItem(menuController.getMenuItemAllExpenses(), getTreasuryButtons().openExpenses());
//            initializeReports(menuController);
//            initializeMainMenuItemsSetting(menuController);
//
//            AppModules.registerModules();
//
//            ModuleContext moduleContext = new ModuleContext(
//                    this,
//                    daoFactory,
//                    menuController.getMenuBar(),
//                    tabPane
//            );
//
//            ModuleRegistry.initializeModules(moduleContext);
//
//            // ✅ إعادة فحص القوائم بعد اكتمال الإعدادات
//            menuController.finalizeMenuVisibility();
//
//            initializePermissionsMenu();
//        } catch (Exception e) {
//            logException(e);
//        }
//
//    }

//    private void sales(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSales(), getTotalSales().addInvoice());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSalesReturn(), getTotalSalesReturn().addInvoice());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalSales(), getTotalSales().totals());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalSalesReturn(), getTotalSalesReturn().totals());
//    }

//    private void purchase(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemPurchase(), getTotalPurchase().addInvoice());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemPurchaseReturn(), getTotalPurchaseReturn().addInvoice());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalPurchase(), getTotalPurchase().totals());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalPurchaseReturn(), getTotalPurchaseReturn().totals());
//    }

//    private void initializeMainMenuItems(MainMenuController menuController) throws Exception {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemItems(), getItemsButtons().addItem());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItem(), getItemsButtons().allItems());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItemFromExcel(), getItemsButtons().addItemsFromExcel());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemUnit(), getItemsButtons().units());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemInventory(), getItemsButtons().inventory());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemMainGroup(), getItemsButtons().addMainGroup());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSupGroup(), getItemsButtons().addSubGroup());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemConvertStock(), getItemsButtons().convertStock());
//    }

//    private void initializePermissionsMenu() {
//        // ✅ مثال على إضافة قائمة الصلاحيات (مع التحقق من الصلاحية)
//        if (PermissionHelper.hasAny(
//                PermissionCode.USERS_PERMISSIONS,
//                PermissionCode.ROLES_SHOW,
//                PermissionCode.USERS_ROLES
//        )) {
//            Menu permissionsMenu = new Menu("الصلاحيات");
//
//            // إدارة الأدوار - فقط إذا كان لديه الصلاحية
//            if (PermissionHelper.has(PermissionCode.ROLES_SHOW)) {
//                MenuItem rolesMenuItem = new MenuItem("إدارة الأدوار");
//                rolesMenuItem.setOnAction(e -> {
//                    try {
//                        getPermissionsButtons().rolesManagement().action();
//                    } catch (Exception ex) {
//                        log.error("خطأ في فتح إدارة الأدوار", ex);
//                    }
//                });
//                permissionsMenu.getItems().add(rolesMenuItem);
//            }
//
//            // صلاحيات المستخدمين
//            if (PermissionHelper.has(PermissionCode.USERS_PERMISSIONS)) {
//                MenuItem userPermissionsMenuItem = new MenuItem("صلاحيات المستخدمين");
//                userPermissionsMenuItem.setOnAction(e -> {
//                    try {
//                        getUsersAll().userPermissions().action();
//                    } catch (Exception ex) {
//                        log.error("خطأ في فتح صلاحيات المستخدمين", ex);
//                    }
//                });
//                permissionsMenu.getItems().add(userPermissionsMenuItem);
//            }
//
//            // أدوار المستخدمين
//            if (PermissionHelper.has(PermissionCode.USERS_ROLES)) {
//                MenuItem userRolesMenuItem = new MenuItem("أدوار المستخدمين");
//                userRolesMenuItem.setOnAction(e -> {
//                    try {
//                        getUsersAll().userRoles().action();
//                    } catch (Exception ex) {
//                        log.error("خطأ في فتح أدوار المستخدمين", ex);
//                    }
//                });
//                permissionsMenu.getItems().add(userRolesMenuItem);
//            }
//
//            // فاصل (فقط إذا كان Admin)
//            if (LogApplication.usersVo.getId() == 1) {
//                SeparatorMenuItem separator = new SeparatorMenuItem();
//
//                // مزامنة الصلاحيات
//                MenuItem syncPermissionsMenuItem = new MenuItem("مزامنة الصلاحيات من الكود");
//                syncPermissionsMenuItem.setOnAction(e -> {
//                    try {
//                        getPermissionsButtons().syncPermissions().action();
//                    } catch (Exception ex) {
//                        log.error("خطأ في مزامنة الصلاحيات", ex);
//                    }
//                });
//
//                // عرض الصلاحيات
//                MenuItem viewPermissionsMenuItem = new MenuItem("عرض معلومات الصلاحيات");
//                viewPermissionsMenuItem.setOnAction(e -> {
//                    try {
//                        getPermissionsButtons().viewAllPermissions().action();
//                    } catch (Exception ex) {
//                        log.error("خطأ في عرض الصلاحيات", ex);
//                    }
//                });
//
//                permissionsMenu.getItems().addAll(
//                        separator,
//                        syncPermissionsMenuItem,
//                        viewPermissionsMenuItem
//                );
//            }
//
//            // إضافة القائمة فقط إذا كان لديها عناصر
//            if (!permissionsMenu.getItems().isEmpty()) {
//                menuController.getMenuBar().getMenus().add(permissionsMenu);
//            }
//        }
//    }

//    private void customers(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddCustomName(), getNameCustomer().addName());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomName(), getNameCustomer().namesData());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomAccount(), getAccountButtonsCustom());
//    }

//    private void suppliers(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddSupplierName(), getNameSup().addName());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersName(), getNameSup().namesData());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersAccount(), getAccountButtonsSup());
//    }

//    private void employees(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemUsers(), getUsersAll().getUsers_all());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddUser(), getUsersAll().getUsers_add());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddEmployee(), getAddEmployee().addEmployee());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemEmployees(), getAddEmployee().employees());
//
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportDelegate(), getDelegatesButtons().performanceReport());
//
//    }

//    private void initializeReports(MainMenuController menuController) throws Exception {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSummary(), getReportsButtons().summaryReport());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportItems(), getReportsButtons().itemsReport());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportItemsDaily(), getReportsButtons().itemsReportDaily());
//
//        var monthlySalesInterface = new MonthlySalesInterface() {
//        };
//
//        var monthlyPurchaseInterface = new MonthlySalesInterface() {
//            @Override
//            public String reportName() {
//                return "تقرير المشتريات السنوي";
//            }
//
//            @Override
//            public String reportTitle() {
//                return "تقرير إجمالي المشتريات الشهرية لكل سنة";
//            }
//
//            @Override
//            public MonthlySalesViewDao getMonthlySalesViewDao(DaoFactory daoFactory) {
//                return daoFactory.monthlyPurchaseViewDao();
//            }
//
//            @Override
//            public String chartTitle() {
//                return "مقارنة المشتريات بين الشهور";
//            }
//        };
//
//
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSalesByYear(), getAction(monthlySalesInterface.reportName(), monthlySalesInterface));
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportPurchaseByYear(), getAction(monthlyPurchaseInterface.reportName(), monthlyPurchaseInterface));

//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomPaid(), getReportsButtons().reportCustomPaid());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersPaid(), getReportsButtons().reportSupplierPaid());
//        // details
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportDetails(), getReportsButtons().detailsReport());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportDelegate(), getReportsButtons().delegateReport());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportYearly(), getReportsButtons().reportYearly());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportProfitLoss(), getReportsButtons().profitLossReport());
//    }

//    private void initializeMainMenuItemsSetting(MainMenuController menuController) {
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemHome(), getSettingButtons().home());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSettingUsers(), getSettingButtons().setting());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemDeleteData(), getSettingButtons().deleteData());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemBackup(), getSettingButtons().backup());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAbout(), getSettingButtons().about());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemClose(), getSettingButtons().close());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemShiftReports(), getSettingButtons().adminShifts());
//    }

//    private void hamburgerAction(JFXDrawer drawer, Node node) {
//        DoubleProperty doubleProperty = new SimpleDoubleProperty(0);
//        drawer.prefWidthProperty().bind(doubleProperty);
//        drawer.setDefaultDrawerSize(doubleProperty.doubleValue());

    /// /        HamburgerBasicCloseTransition transition = new HamburgerBasicCloseTransition(hamburger);
//        JFXHamburger hamburger = toolbarController.getHamburger();
//        HamburgerBackArrowBasicTransition transition = new HamburgerBackArrowBasicTransition(hamburger);
//        transition.setRate(-1);
//        hamburger.setOnMouseClicked(mouseEvent -> {
//            transition.setRate(transition.getRate() * -1);
//            transition.play();
//            if (drawer.isOpened()) {
//                drawer.close();
//                drawer.getSidePane().clear();
//                doubleProperty.set(0);
//            } else {
//                drawer.open();
//                doubleProperty.set(200);
//                drawer.setSidePane(node);
//            }
//        });
//    }
    private ButtonWithPerm getAction(String name, MonthlySalesInterface monthlySalesInterface) {
        return new ButtonWithPerm() {

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
            box.getChildren().add(new ModernDashboardApp(daoFactory).getPane());
            VBox.setVgrow(box, Priority.ALWAYS);
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

    private void dontShowData() {

    }

    private void logException(Exception e) {
        log.error(e.getMessage(), e.getCause() + " - " + this.getClass().getName());
        AllAlerts.showExceptionDialog(e);
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
        checkAndHidePaneIfEmpty(paneReports);
        checkAndHidePaneIfEmpty(paneSetting);
    }

    /**
     * التحقق من وجود أزرار مرئية في الـ TitledPane
     */
    private void checkAndHidePaneIfEmpty(TitledPane pane) {
        if (pane.getContent() instanceof javafx.scene.layout.Pane contentPane) {
            boolean hasVisibleButton = contentPane.getChildren().stream()
                    .filter(node -> node instanceof Button)
                    .anyMatch(Node::isVisible);

            if (!hasVisibleButton) {
                pane.setVisible(false);
                pane.setManaged(false);
            }
        }
    }

    private void titlePaneSetting(TitledPane titledPane, String text, InputStream stream) {
        titledPane.setText(text);
        titledPane.setGraphic(new ImageDesign(stream, 20));
    }

//    private void selectBackgroundImage() {
//        FileChooser fileChooser = new FileChooser();
//        fileChooser.getExtensionFilters().add(
//                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png")
//        );
//        File selectedFile = fileChooser.showOpenDialog(null);
//        if (selectedFile != null) {
//            try {
//                setPathImageMainScreen(selectedFile.getPath());
//                setBackgroundImage();
//            } catch (Exception e) {
//                logException(e);
//            }
//        }
//    }

//    private void action() {
////        btnAnyDesk.setOnAction(e -> {
////            try {
////                var file = new File("program/AnyDesk.exe");
////                if (!file.exists()) {
////                    throw new FileNotFoundException("File not found");
////                }
////                Desktop.getDesktop().open(file);
////            } catch (IOException ex) {
////                logException(ex);
////            }
////        });
//
////        btnAnyDesk.setTooltip(new Tooltip("Program AnyDesk"));
////        btnChooseImage.setTooltip(new Tooltip("تغيير الخلفية"));
////        btnChooseImage.setOnAction(e -> selectBackgroundImage());
////        btnChooseImage.setOnAction(e -> onChooseFolderClick());
//    }

//    private void showImage() {
//        slideshow = new BackgroundSlideshow(mainPane, /*shuffle*/ false, /*refreshEachCycle*/ true);
//        // Create context menu for slideshow control
//        MenuItem nextImageItem = new MenuItem("Next Image");
//        nextImageItem.setOnAction(e -> slideshow.showNextImage());
//        slideshowMenu.getItems().add(nextImageItem);
//        mainPane.setOnContextMenuRequested(e ->
//                slideshowMenu.show(mainPane, e.getScreenX(), e.getScreenY())
//        );
//    }
}
