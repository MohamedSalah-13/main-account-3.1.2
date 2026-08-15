package com.hamza.account.controller.main;

import com.hamza.account.config.FxmlConstants;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.controller.reports.MonthlySalesInterface;
import com.hamza.account.features.events.UserRenamed;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.MonthlySalesViewDao;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.view.MonthlyView;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.EventBus;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPathImageMainScreen;
import static com.hamza.account.config.PropertiesName.getShowMainTotals;

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

    }

    private void otherSetting() {
        try {
            tabPane.getTabs().getFirst().setText(Setting_Language.WORD_MAIN);
            tabPane.getTabs().getFirst().setClosable(false);
            getRightPane();
        } catch (Exception e) {
            logException(e);
        }
    }

    private void getRightPane() throws Exception {
        FXMLLoader fxmlLoader = new FxmlConstants().rightPane;
        Pane pane = fxmlLoader.load();
        // the side menu is now the only navigation surface, so it is added directly
        // and stays visible for the lifetime of the main screen.
        mainContentBox.getChildren().addFirst(pane);
        MainRightPaneController mainRightPaneController = fxmlLoader.getController();

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
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSales(), getTotalSales().addInvoice());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSalesReturn(), getTotalSalesReturn().addInvoice());
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
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSubGroup(), getItemsButtons().addSubGroup());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnArea(), getItemsButtons().areasList());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnInventory(), getItemsButtons().inventory());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnStockCount(), getItemsButtons().stockCount());
        /*----------------------------------------------- Custom -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddCustomerName(), getNameCustomer().addName());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnCustomer(), getNameCustomer().namesData());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAccountCustom(), getAccountButtonsCustom());
        /*----------------------------------------------- Suppliers -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddSupplierName(), getNameSup().addName());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSuppliers(), getNameSup().namesData());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAccountSuppliers(), getAccountButtonsSup());
        /*----------------------------------------------- Employees -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddEmployee(), getAddEmployee().addEmployee());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnEmployees(), getAddEmployee().employees());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAddUser(), getUsersAll().getUsers_add());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnUsers(), getUsersAll().getUsers_all());
        /*----------------------------------------------- Treasury -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnTreasuryDetails(), getTreasuryButtons().treasuryDetails());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnProcess(), getTreasuryButtons().openProcess());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnExpenses(), getTreasuryButtons().openExpenses());
        /*----------------------------------------------- Reports -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportSummary(), getReportsButtons().summaryReport());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportItems(), getReportsButtons().itemsReport());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportItemsDaily(), getReportsButtons().itemsReportDaily());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportSalesByYear(), getAction(monthlySalesInterface.reportName(), monthlySalesInterface));
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportPurchaseByYear(), getAction(monthlyPurchaseInterface.reportName(), monthlyPurchaseInterface));
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportCustomPaid(), getReportsButtons().reportCustomPaid());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportSuppliersPaid(), getReportsButtons().reportSupplierPaid());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportDetails(), getReportsButtons().detailsReport());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportYearly(), getReportsButtons().reportYearly());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnReportProfitLoss(), getReportsButtons().profitLossReport());
        /*----------------------------------------------- Setting -----------------------------------------------*/
        menuButtonSetting.configureButton(mainRightPaneController.getBtnHome(), getSettingButtons().home());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnSetting(), getSettingButtons().setting());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnShiftReports(), getSettingButtons().adminShifts());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnBackup(), getSettingButtons().backup());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnDeleteData(), getSettingButtons().deleteData());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnAbout(), getSettingButtons().about());
        menuButtonSetting.configureButton(mainRightPaneController.getBtnClose(), getSettingButtons().close());
        /*----------------------------------------------- User menu (moved from the removed top toolbar) -----------------------------------------------*/
        menuButtonSetting.initializeMenuItem(mainRightPaneController.getMenuItemChangeName(), getForAllButtons().changeName());
        menuButtonSetting.initializeMenuItem(mainRightPaneController.getMenuItemChangePass(), getForAllButtons().changePassword());
        menuButtonSetting.initializeMenuItem(mainRightPaneController.getMenuItemLogout(), getForAllButtons().logout());
        // Every signed-in user may log out, regardless of what other permissions they hold.
        mainRightPaneController.getMenuItemLogout().setDisable(false);

        dontShowData(mainRightPaneController);
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


    private void dontShowData(MainRightPaneController mainRightPaneController) {
        var permissionDisableService = new DisableButtons.PermissionDisableService();
        permissionDisableService.applyPermissionBasedDisable(mainRightPaneController.getPaneEmployees(), AppPermissions.EMPLOYEE_SHOW);
        permissionDisableService.applyPermissionBasedDisable(mainRightPaneController.getPaneSetting(), AppPermissions.SETTING_SHOW);
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
