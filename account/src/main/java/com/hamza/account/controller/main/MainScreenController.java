package com.hamza.account.controller.main;

import com.hamza.account.config.FxmlConstants;
import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.controller.reports.ReportTotalsByYearAndMonthController;
import com.hamza.account.dash.ReportByDate;
import com.hamza.account.interfaces.treeAccount.ReportTreeAccountCustom;
import com.hamza.account.interfaces.treeAccount.ReportTreeAccountSuppliers;
import com.hamza.account.interfaces.treePurchase.ReportTreePurchase;
import com.hamza.account.interfaces.treePurchase.ReportTreeSales;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.notification.ItemNotifications;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.AccountCustomerService;
import com.hamza.account.service.AccountSupplierService;
import com.hamza.account.service.TotalBuyService;
import com.hamza.account.service.TotalSalesService;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.view.BoxApplication;
import com.hamza.controlsfx.view.DateTimeApplication;
import com.jfoenix.controls.JFXDrawer;
import com.jfoenix.controls.JFXHamburger;
import com.jfoenix.transitions.hamburger.HamburgerBackArrowBasicTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.*;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class MainScreenController extends MainItems implements Initializable {

    private final StringProperty sumTotalsPurchase = new SimpleStringProperty("0");
    private final StringProperty sumTotalsSales = new SimpleStringProperty("0");
    private final StringProperty sumTotalsCustomerAccounts = new SimpleStringProperty("0");
    private final StringProperty sumTotalsSuppliersAccounts = new SimpleStringProperty("0");
    private final TotalSalesService totalSalesService;
    private final TotalBuyService totalBuyService;
    private final AccountCustomerService accountCustomerService;
    private final AccountSupplierService accountSupplierService;
    private final ServiceData serviceData;
    private final Publisher<String> publisherAddUser;
    private final ContextMenu slideshowMenu = new ContextMenu();
    public Pane mainPane;
    private MainMenuController menuController;
    @FXML
    private MenuBar menuBar;
    @Getter
    @FXML
    private TabPane tabPane;
    @FXML
    private BorderPane borderPane;
    @FXML
    private FlowPane flowPane;
    @FXML
    private VBox boxCenter;
    @FXML
    private JFXDrawer drawer;
    @FXML
    private ImageView imageView;
    @FXML
    private Button btnAnyDesk, btnChooseImage;
    @FXML
    private BorderPane paneAnyDesk;
    private MenuButtonSetting menuButtonSetting;
    private MainToolbarController toolbarController;
    private BackgroundSlideshow slideshow;

    public MainScreenController(LoadDataAndList loadDataAndList, DaoFactory daoFactory) throws Exception {
        super(daoFactory, loadDataAndList);
        this.serviceData = new ServiceData(daoFactory);
        this.totalSalesService = serviceData.getTotalSalesService();
        this.totalBuyService = serviceData.getTotalBuyService();
        this.accountCustomerService = serviceData.getAccountCustomerService();
        this.accountSupplierService = serviceData.getAccountSupplierService();
        this.publisherAddUser = getPublisherAddUser();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        menuButtonSetting = new MenuButtonSetting(tabPane);
        mainPane = (Pane) borderPane.getCenter();
//        showImage();
        menuBarSetting();
        mainToolbarSetting();
        otherSetting();
        setBackgroundImage();
        action();
        dontShowData();
        addTabContextMenu();

        if (LogApplication.usersVo.getId() == 1) {
            if (getShowMainTotals()) firstBoxInMain();
        }

        // check to show
        notifyItems();

        // data publisher
        var name = LogApplication.usersVo.getUsername();
        publisherAddUser.setAvailability(name);
        getPublisherSales().addObserver(message -> sumTotalsSales.setValue(String.valueOf(roundToTwoDecimalPlaces(getNumber()))));
        getPublisherBuy().addObserver(message -> sumTotalsPurchase.setValue(String.valueOf(roundToTwoDecimalPlaces(getSumTotal()))));
        getPublisherAddAccountCustom().addObserver(message ->
                sumTotalsCustomerAccounts.setValue(String.valueOf(roundToTwoDecimalPlaces(accountCustomerService.sumTotal()))));
        getPublisherAddAccountSuppliers().addObserver(message ->
                sumTotalsSuppliersAccounts.setValue(String.valueOf(roundToTwoDecimalPlaces(accountSupplierService.sumTotal()))));

        getChangeMainScreenImage().addObserver(message -> setBackgroundImage());
        getShowMainTotalsScreen().addObserver(message -> {
            if (message == true) {
                firstBoxInMain();
            } else {
                flowPane.getChildren().clear();
            }
        });

        initializeDataRefresh();
//        if (getSettingServerStart()) {
//            new UpdateData(this).loadAllData();
//        }
    }

    private double getSumTotal() {
        try {
            return totalBuyService.sumTotal();
        } catch (DaoException e) {
            logException(e);
            return 0;
        }
    }

    private double getNumber() {
        try {
            return totalSalesService.sumTotal();
        } catch (DaoException e) {
            logException(e);
            return 0;
        }
    }


    private void notifyItems() {
        if (getItemShowAlert()) {
            var size = getItemsMiniQuantities().size();
            if (size > 0) {
                new ItemNotifications(getItemsMiniQuantities());
            }
        }
    }

    private java.util.List<ItemsMiniQuantity> getItemsMiniQuantities() {
        try {
            return this.serviceData.getItemMiniQuantityService().itemsMiniQuantityList();
        } catch (DaoException e) {
            logException(e);
            return new ArrayList<>();
        }
    }

    private void initializeDataRefresh() {
        sumTotalsSales.setValue(String.valueOf(roundToTwoDecimalPlaces(getNumber())));
        sumTotalsPurchase.setValue(String.valueOf(roundToTwoDecimalPlaces(getSumTotal())));
        sumTotalsCustomerAccounts.setValue(String.valueOf(roundToTwoDecimalPlaces(accountCustomerService.sumTotal())));
        sumTotalsSuppliersAccounts.setValue(String.valueOf(roundToTwoDecimalPlaces(accountSupplierService.sumTotal())));

    }

    private void otherSetting() {
        try {
            DateTimeApplication dateTimeApplication = new DateTimeApplication();
            borderPane.setBottom(dateTimeApplication.getPane());
            tabPane.getTabs().getFirst().setText(Setting_Language.WORD_MAIN);
            tabPane.getTabs().getFirst().setClosable(false);
            getRightPane();
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
        menuButtonSetting.configureButton(mainRightPaneController.getBtnItems(), getItemsButtons().allItems(this));
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
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItem(), getItemsButtons().allItems(this));
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemAddItemFromExcel(), getItemsButtons().addItemsFromExcel());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemUnit(), getItemsButtons().units());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemInventory(), getItemsButtons().inventory());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemMiniQuantity(), getItemsButtons().miniQuantityItems(getItemsMiniQuantities()));
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
        // purchase - sales
        ReportTreePurchase treePurchase = new ReportTreePurchase(daoFactory, this, dataInterfacePurchase);
        ReportTreeSales treeSales = new ReportTreeSales(daoFactory, this);
        ReportByDate<Purchase, Suppliers> actionPurchase = new ReportByDate<>(this, treePurchase
                , dataInterfacePurchase.nameData(), UserPermissionType.DISABLE_BUTTON);
        ReportByDate<Sales, Customers> actionSales = new ReportByDate<>(this, treeSales
                , dataInterfaceSales.nameData(), UserPermissionType.DISABLE_BUTTON);

        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportPurchase(), actionPurchase);
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSales(), actionSales);

        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSalesByYear(), getAction(new ReportTotalsByYearAndMonthController<>(dataInterfaceSales), "مبيعات الكل"));
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportPurchaseByYear(), getAction(new ReportTotalsByYearAndMonthController<>(dataInterfacePurchase), "مشتريات الكل"));


        ReportTreeAccountCustom treeAccountCustom = new ReportTreeAccountCustom(this.getDaoFactory(), this) {
        };
        ReportTreeAccountSuppliers treeAccountSuppliers = new ReportTreeAccountSuppliers(this.getDaoFactory(), this);
        ReportByDate<CustomerAccount, Customers> accountCustom = new ReportByDate<>(this, treeAccountCustom
                , dataInterfaceSales.nameData(), UserPermissionType.REPORTS_SHOW_CUSTOMERS);
        ReportByDate<SupplierAccount, Suppliers> accountSuppliers = new ReportByDate<>(this, treeAccountSuppliers
                , dataInterfacePurchase.nameData(), UserPermissionType.REPORTS_SHOW_SUPPLIERS);
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportCustom(), accountCustom);
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemCustomPaid(), getReportsButtons().reportCustomPaid());
        menuButtonSetting.initializeMenuItem(menuController.getMenuItemReportSuppliers(), accountSuppliers);
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
    }

    private <T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> ButtonWithPerm getAction(
            ReportTotalsByYearAndMonthController<T1, T2, T3, T4> controller, String name) {
        String sales = "مبيعات";
        return new ButtonWithPerm() {
            @Override
            public UserPermissionType getPermissionType() {
                if (name.contains(sales))
                    return UserPermissionType.REPORTS_SHOW_SALES;
                else return UserPermissionType.REPORTS_SHOW_PURCHASE;
            }

            @Override
            public void action() {

            }

            @NotNull
            @Override
            public String textName() {
                return name;
            }

            @Override
            public void actionAddPaneToTabPane(TabPane tabPane) throws Exception {
                var setting24 = new Image_Setting().shoppingPurchase;
                if (name.equals(sales))
                    setting24 = new Image_Setting().shoppingSales;

                addTape(tabPane, new OpenFxmlApplication(controller).getPane(), textName(), setting24);
            }

            @Override
            public boolean showOnTapPane() {
                return true;
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
        flowPane.getChildren().clear();
        Image_Setting imageSetting = new Image_Setting();
        addBoxData(sumTotalsSales, Setting_Language.WORD_SALES, "red", imageSetting.shoppingSales);
        addBoxData(sumTotalsPurchase, Setting_Language.WORD_PUR, "1", imageSetting.shoppingPurchase);
        addBoxData(sumTotalsCustomerAccounts, Setting_Language.WORD_CUSTOM_ACC, "green", imageSetting.vertical_align_bottom);
        addBoxData(sumTotalsSuppliersAccounts, Setting_Language.WORD_SUP_ACC, "yellow", imageSetting.vertical_align_top);
    }

    private void addBoxData(StringProperty stringProperty, String title, String color, InputStream image) {
        try {
            BoxApplication boxApplication = new BoxApplication(title, image, color);
            boxApplication.getBoxController().textSumTotalsProperty().bind(stringProperty);
            Pane pane = boxApplication.getPane();
            pane.setOnMouseClicked(mouseEvent -> {
                try {
                    if (title.equals(Setting_Language.WORD_SALES)) menuController.getMenuItemTotalSales().fire();
                    if (title.equals(Setting_Language.WORD_PUR)) menuController.getMenuItemTotalPurchase().fire();
                    if (title.equals(Setting_Language.WORD_CUSTOM_ACC))
                        menuController.getMenuItemCustomAccount().fire();
                    if (title.equals(Setting_Language.WORD_SUP_ACC))
                        menuController.getMenuItemSuppliersAccount().fire();
                } catch (Exception e) {
                    logException(e);
                }
            });

            flowPane.getChildren().add(pane);
        } catch (Exception e) {
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
        btnAnyDesk.setOnAction(e -> {
            try {
                var file = new File("program/AnyDesk.exe");
                if (!file.exists()) {
                    throw new FileNotFoundException("File not found");
                }
                Desktop.getDesktop().open(file);
            } catch (IOException ex) {
                logException(ex);
            }
        });

        btnAnyDesk.setTooltip(new Tooltip("Program AnyDesk"));
        btnChooseImage.setTooltip(new Tooltip("تغيير الخلفية"));
        btnChooseImage.setOnAction(e -> selectBackgroundImage());
//        btnChooseImage.setOnAction(e -> onChooseFolderClick());
    }

    private void dontShowData() {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(menuController.getMenuEmployees(), UserPermissionType.EMPLOYEE_SHOW);
        permissionDisableService.applyPermissionBasedDisable(menuController.getMenuSetting(), UserPermissionType.SETTING_SHOW);

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

    // Bind to your "Choose folder" button
    public void onChooseFolderClick() {
        // replace with an actual Window, e.g., mainPane.getScene().getWindow()
        slideshow.chooseFolderAndStart(mainPane.getScene().getWindow());
    }

    // Call this when closing the app/window to clean up
    public void onClose() {
        slideshow.stop();
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
