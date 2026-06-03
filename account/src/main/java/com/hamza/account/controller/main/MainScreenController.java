package com.hamza.account.controller.main;

import com.hamza.account.config.FxmlConstants;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.MonthlyView;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.jfoenix.controls.JFXDrawer;
import com.jfoenix.controls.JFXHamburger;
import com.jfoenix.transitions.hamburger.HamburgerBackArrowBasicTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;

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
    private VBox boxCenter;
    @FXML
    private JFXDrawer drawer;
    @FXML
    private VBox box;

    private MenuButtonSetting menuButtonSetting;
    private MainToolbarController toolbarController;
    private BackgroundSlideshow slideshow;

    public MainScreenController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.publisherAddUser = getPublisherAddUser();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        menuButtonSetting = new MenuButtonSetting(tabPane);
        menuBarSetting();
        dontShowData();
        mainToolbarSetting();
        otherSetting();
        addTabContextMenu();

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


    private void otherSetting() {
        try {
//            borderPane.setBottom(fxmlTimePane.load());
            tabPane.getTabs().getFirst().setText(Setting_Language.WORD_MAIN);
            tabPane.getTabs().getFirst().setClosable(false);
            getRightPane();

            // ✅ مثال على إضافة قائمة الصلاحيات (مع التحقق من الصلاحية)
            if (PermissionHelper.hasAny(
                    PermissionCode.USERS_PERMISSIONS,
                    PermissionCode.ROLES_SHOW,
                    PermissionCode.USERS_ROLES
            )) {
                Menu permissionsMenu = new Menu("الصلاحيات");

                // إدارة الأدوار - فقط إذا كان لديه الصلاحية
                if (PermissionHelper.has(PermissionCode.ROLES_SHOW)) {
                    MenuItem rolesMenuItem = new MenuItem("إدارة الأدوار");
                    rolesMenuItem.setOnAction(e -> {
                        try {
                            getPermissionsButtons().rolesManagement().action();
                        } catch (Exception ex) {
                            log.error("خطأ في فتح إدارة الأدوار", ex);
                        }
                    });
                    permissionsMenu.getItems().add(rolesMenuItem);
                }

                // صلاحيات المستخدمين
                if (PermissionHelper.has(PermissionCode.USERS_PERMISSIONS)) {
                    MenuItem userPermissionsMenuItem = new MenuItem("صلاحيات المستخدمين");
                    userPermissionsMenuItem.setOnAction(e -> {
                        try {
                            getUsersAll().userPermissions().action();
                        } catch (Exception ex) {
                            log.error("خطأ في فتح صلاحيات المستخدمين", ex);
                        }
                    });
                    permissionsMenu.getItems().add(userPermissionsMenuItem);
                }

                // أدوار المستخدمين
                if (PermissionHelper.has(PermissionCode.USERS_ROLES)) {
                    MenuItem userRolesMenuItem = new MenuItem("أدوار المستخدمين");
                    userRolesMenuItem.setOnAction(e -> {
                        try {
                            getUsersAll().userRoles().action();
                        } catch (Exception ex) {
                            log.error("خطأ في فتح أدوار المستخدمين", ex);
                        }
                    });
                    permissionsMenu.getItems().add(userRolesMenuItem);
                }

                // فاصل (فقط إذا كان Admin)
                if (LogApplication.usersVo.getId() == 1) {
                    SeparatorMenuItem separator = new SeparatorMenuItem();

                    // مزامنة الصلاحيات
                    MenuItem syncPermissionsMenuItem = new MenuItem("مزامنة الصلاحيات من الكود");
                    syncPermissionsMenuItem.setOnAction(e -> {
                        try {
                            getPermissionsButtons().syncPermissions().action();
                        } catch (Exception ex) {
                            log.error("خطأ في مزامنة الصلاحيات", ex);
                        }
                    });

                    // عرض الصلاحيات
                    MenuItem viewPermissionsMenuItem = new MenuItem("عرض معلومات الصلاحيات");
                    viewPermissionsMenuItem.setOnAction(e -> {
                        try {
                            getPermissionsButtons().viewAllPermissions().action();
                        } catch (Exception ex) {
                            log.error("خطأ في عرض الصلاحيات", ex);
                        }
                    });

                    permissionsMenu.getItems().addAll(
                            separator,
                            syncPermissionsMenuItem,
                            viewPermissionsMenuItem
                    );
                }

                // إضافة القائمة فقط إذا كان لديها عناصر
                if (!permissionsMenu.getItems().isEmpty()) {
                    menuController.getMenuBar().getMenus().add(permissionsMenu);
                }
            }
        } catch (Exception e) {
            logException(e);
        }
    }

    private void mainToolbarSetting() {
        try {
            FXMLLoader fxmlLoader = new FxmlConstants().mainToolbar;
            toolbarController = new MainToolbarController(this, this);
            fxmlLoader.setController(toolbarController);
            ToolBar pane = fxmlLoader.load();
            boxCenter.getChildren().addFirst(pane);
        } catch (IOException e) {
            logException(e);
        }
    }

    private void getRightPane() throws Exception {
        FXMLLoader fxmlLoader = new FxmlConstants().rightPane;
        Pane pane = fxmlLoader.load();
        // add to drawer
        hamburgerAction(drawer, pane);
        // button action
        MainRightPaneController mainRightPaneController = fxmlLoader.getController();

        /*----------------------------------------------- Sales -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSales(), getTotalSales().addInvoice());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnTotalSale(), getTotalSales().totals());
        /*----------------------------------------------- Purchase -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnPurchase(), getTotalPurchase().addInvoice());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnTotalPurchase(), getTotalPurchase().totals());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnPurchaseRe(), getTotalPurchaseReturn().addInvoice());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnTotalPurchaseRe(), getTotalPurchaseReturn().totals());
        /*----------------------------------------------- Items -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnItems(), getItemsButtons().allItems());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddItem(), getItemsButtons().addItem());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnUnits(), getItemsButtons().units());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnMainGroup(), getItemsButtons().addMainGroup());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnInventory(), getItemsButtons().inventory());
        /*----------------------------------------------- Custom -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnCustomer(), getNameCustomer().namesData());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAccountCustom(), getAccountButtonsCustom());
        /*----------------------------------------------- Suppliers -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSuppliers(), getNameSup().namesData());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAccountSuppliers(), getAccountButtonsSup());
        /*----------------------------------------------- Employees -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddDeposit(), getTreasuryButtons().addDeposit());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnTreasuryDetails(), getTreasuryButtons().treasuryDetails());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnConvertTreasury(), getTreasuryButtons().convertTreasury());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnProcess(), getTreasuryButtons().openProcess());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnExpenses(), getTreasuryButtons().openExpenses());
        /*----------------------------------------------- Setting -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnHome(), getSettingButtons().home());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnBackup(), getSettingButtons().backup());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnUsers(), getUsersAll().getUsers_all());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnClose(), getSettingButtons().close());
    }

    private void menuBarSetting() {
        try {
            FXMLLoader fxmlLoader = new FxmlConstants().menuBar;
            MenuBar paneMenuBar = fxmlLoader.load();
            // add to border pane
            borderPane.setTop(paneMenuBar);
            menuController = fxmlLoader.getController();
            sales(menuController);
            purchase(menuController);
            initializeMainMenuItems(menuController);
            customers(menuController);
            suppliers(menuController);
            employees(menuController);
            menuButtonSetting.initializeMenuItem(menuController.getMenuItemAllExpenses(), getTreasuryButtons().openExpenses());
            initializeReports(menuController);
            initializeMainMenuItemsSetting(menuController);

            // ✅ إعادة فحص القوائم بعد اكتمال الإعدادات
            menuController.finalizeMenuVisibility();
        } catch (Exception e) {
            logException(e);
        }

    }

    private void sales(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSales(), getTotalSales().addInvoice());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSalesReturn(), getTotalSalesReturn().addInvoice());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalSales(), getTotalSales().totals());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalSalesReturn(), getTotalSalesReturn().totals());
    }

    private void purchase(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemPurchase(), getTotalPurchase().addInvoice());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemPurchaseReturn(), getTotalPurchaseReturn().addInvoice());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalPurchase(), getTotalPurchase().totals());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemTotalPurchaseReturn(), getTotalPurchaseReturn().totals());
    }

    private void initializeMainMenuItems(MainMenuController menuController) throws Exception {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemItems(), getItemsButtons().addItem());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItem(), getItemsButtons().allItems());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItemFromExcel(), getItemsButtons().addItemsFromExcel());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemUnit(), getItemsButtons().units());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemInventory(), getItemsButtons().inventory());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemMainGroup(), getItemsButtons().addMainGroup());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSupGroup(), getItemsButtons().addSubGroup());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemConvertStock(), getItemsButtons().convertStock());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemArea(), getItemsButtons().areasList());
    }

    private void customers(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddCustomName(), getNameCustomer().addName());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomName(), getNameCustomer().namesData());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomAccount(), getAccountButtonsCustom());
    }

    private void suppliers(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddSupplierName(), getNameSup().addName());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersName(), getNameSup().namesData());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersAccount(), getAccountButtonsSup());
    }

    private void employees(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemUsers(), getUsersAll().getUsers_all());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddUser(), getUsersAll().getUsers_add());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddEmployee(), getAddEmployee().addEmployee());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemEmployees(), getAddEmployee().employees());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddTargetDelegate(), getAddEmployee().addTarget());
    }

    private void initializeReports(MainMenuController menuController) throws Exception {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSummary(), getReportsButtons().summaryReport());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportItems(), getReportsButtons().itemsReport());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportItemsDaily(), getReportsButtons().itemsReportDaily());

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


        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSalesByYear(), getAction(monthlySalesInterface.reportName(), monthlySalesInterface));
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportPurchaseByYear(), getAction(monthlyPurchaseInterface.reportName(), monthlyPurchaseInterface));

//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportCustom(), null);
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomPaid(), getReportsButtons().reportCustomPaid());
//        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSuppliers(), null);
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSuppliersPaid(), getReportsButtons().reportSupplierPaid());
        // details
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportDetails(), getReportsButtons().detailsReport());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportDelegate(), getReportsButtons().delegateReport());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportYearly(), getReportsButtons().reportYearly());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportProfitLoss(), getReportsButtons().profitLossReport());
    }

    private void initializeMainMenuItemsSetting(MainMenuController menuController) {
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemHome(), getSettingButtons().home());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemSettingUsers(), getSettingButtons().setting());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemDeleteData(), getSettingButtons().deleteData());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemBackup(), getSettingButtons().backup());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAbout(), getSettingButtons().about());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemClose(), getSettingButtons().close());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemShiftReports(), getSettingButtons().adminShifts());
    }

    private ButtonWithPerm getAction(String name, MonthlySalesInterface monthlySalesInterface) {
        String sales = "مبيعات";
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

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
            }

        };
    }

    private void hamburgerAction(JFXDrawer drawer, Node node) {
        DoubleProperty doubleProperty = new SimpleDoubleProperty(0);
        drawer.prefWidthProperty().bind(doubleProperty);
        drawer.setDefaultDrawerSize(doubleProperty.doubleValue());
//        HamburgerBasicCloseTransition transition = new HamburgerBasicCloseTransition(hamburger);
        JFXHamburger hamburger = toolbarController.getHamburger();
        HamburgerBackArrowBasicTransition transition = new HamburgerBackArrowBasicTransition(hamburger);
        transition.setRate(-1);
        hamburger.setOnMouseClicked(mouseEvent -> {
            transition.setRate(transition.getRate() * -1);
            transition.play();
            if (drawer.isOpened()) {
                drawer.close();
                drawer.getSidePane().clear();
                doubleProperty.set(0);
            } else {
                drawer.open();
                doubleProperty.set(200);
                drawer.setSidePane(node);
            }
        });
    }

    private void firstBoxInMain() {
        try {
            box.getChildren().clear();
            box.getChildren().add(new ModernDashboardApp(daoFactory).getPane());
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

    private void selectBackgroundImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png")
        );
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            try {
                setPathImageMainScreen(selectedFile.getPath());
                setBackgroundImage();
            } catch (Exception e) {
                logException(e);
            }
        }
    }

    private void logException(Exception e) {
        log.error(e.getMessage(), e.getCause() + " - " + this.getClass().getName());
        AllAlerts.showExceptionDialog(e);
    }

    private void action() {
//        btnAnyDesk.setOnAction(e -> {
//            try {
//                var file = new File("program/AnyDesk.exe");
//                if (!file.exists()) {
//                    throw new FileNotFoundException("File not found");
//                }
//                Desktop.getDesktop().open(file);
//            } catch (IOException ex) {
//                logException(ex);
//            }
//        });

//        btnAnyDesk.setTooltip(new Tooltip("Program AnyDesk"));
//        btnChooseImage.setTooltip(new Tooltip("تغيير الخلفية"));
//        btnChooseImage.setOnAction(e -> selectBackgroundImage());
//        btnChooseImage.setOnAction(e -> onChooseFolderClick());
    }

    private void dontShowData() {

    }

    private void showImage() {
        slideshow = new BackgroundSlideshow(mainPane, /*shuffle*/ false, /*refreshEachCycle*/ true);
        // Create context menu for slideshow control
        MenuItem nextImageItem = new MenuItem("Next Image");
        nextImageItem.setOnAction(e -> slideshow.showNextImage());
        slideshowMenu.getItems().add(nextImageItem);
        mainPane.setOnContextMenuRequested(e ->
                slideshowMenu.show(mainPane, e.getScreenX(), e.getScreenY())
        );
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
