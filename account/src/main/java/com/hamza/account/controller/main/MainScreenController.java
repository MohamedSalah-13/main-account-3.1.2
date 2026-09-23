package com.hamza.account.controller.main;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.reports.ModernDashboardApp;
import com.hamza.account.features.company.CompanyLogo;
import com.hamza.account.features.company.CompanyService;
import com.hamza.account.features.events.CompanyChanged;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.features.events.UserRenamed;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.productprofile.ProductFeatureAccess;
import com.hamza.account.features.productprofile.ProductFeatures;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.shortcuts.SidebarShortcut;
import com.hamza.account.features.shortcuts.SidebarShortcutManager;
import com.hamza.account.features.shift.ShiftMode;
import com.hamza.account.features.shift.ShiftPolicyChanged;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Company;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPathImageMainScreen;
import static com.hamza.account.config.PropertiesName.getShowMainTotals;
import static com.hamza.controlsfx.language.Setting_Language.*;

public class MainScreenController extends MainItems implements Initializable {

    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final ProductFeatureAccess productFeatures = ServiceRegistry.get(ProductFeatureAccess.class);
    private final Subscriptions subscriptions = new Subscriptions();
    /** True once configureAllButtons has run, so the shortcut map reflects the edition. */
    private boolean sidebarReady;
    /**
     * Below this window height the sidebar switches to its compact paddings. A maximized
     * window on a 1366x768 screen is about 730 points tall; a 1080p one is about 1050.
     */
    private static final double COMPACT_SIDEBAR_HEIGHT = 800;
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
    /** Every sidebar section in the FXML's order; the accordion holds the ones this user can use. */
    private List<TitledPane> allSections;
    @FXML
    private AnchorPane rightPaneRoot;
    @FXML
    private VBox sidebarStack;
    @FXML
    private ScrollPane sideMenuScroll;
    @FXML
    private VBox sideMenuBox;
    @FXML
    private Accordion sideAccordion;
    @FXML
    private Button btnSales, btnQuickSales, btnQuickPurchase, btnSalesReturn, btnTotalSale, btnTotalSalesReturn, btnPurchase, btnTotalPurchase, btnPurchaseRe, btnTotalPurchaseRe, btnItems, btnItemGroups,
            btnAddItem, btnMasterData, btnInventory, btnStockCount, btnStocks, btnStockTransfers, btnMergeItems, btnPriceCheck,
            btnAddCustomerName, btnCustomer, btnAccountCustom, btnAddSupplierName, btnSuppliers,
            btnAccountSuppliers, btnAddEmployee, btnEmployees, btnAddUser, btnUsers,
            btnTreasuries, btnCurrencies, btnTreasuryCash, btnTreasuryTransfer, btnTreasuryCapital, btnTreasuryDetails, btnProcess, btnExpenses,
            btnReportHub, btnReportSummary, btnReportItemSales, btnReportMonthlyTotals,
            btnReportPartyPayments, btnReportYearly, btnReportProfitLoss,
            btnReportReturnReasons,
            btnHome, btnSetting, btnMyShift, btnShiftReports, btnBackup, btnDeleteData, btnAbout, btnClose;
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
        menuButtonSetting = new MenuButtonSetting(tabPane, productFeatures);
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
            tabPane.getTabs().getFirst().setGraphic(AppIcon.HOME.graphic(20));
        } catch (Exception e) {
            logException(e);
        }
    }

    private void setupRightPane() throws Exception {
        applySidebarDirection();
        setupResponsiveSidebar();
        rightPaneSetting();
        setupBrand();
        setupUser();
        setupNotificationBell();
        setupShiftPolicyVisibility();
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
            applySidebarDirection();
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
        /*----------------------------------------------- Sales -----------------------------------------------*/
        menuButtonSetting.configureButton(btnSales, getTotalSales().addInvoice(), ProductFeatures.SALES_CREATE);
        menuButtonSetting.configureButton(btnQuickSales, getTotalSales().addQuickInvoice(), ProductFeatures.SALES_CREATE);
        menuButtonSetting.configureButton(btnSalesReturn, getTotalSalesReturn().addInvoice(), ProductFeatures.SALES_RETURN_CREATE);
        menuButtonSetting.configureButton(btnTotalSale, getTotalSales().totals(), ProductFeatures.SALES_LIST);
        menuButtonSetting.configureButton(btnTotalSalesReturn, getTotalSalesReturn().totals(), ProductFeatures.SALES_RETURN_LIST);
        /*----------------------------------------------- Purchase -----------------------------------------------*/
        menuButtonSetting.configureButton(btnPurchase, getTotalPurchase().addInvoice(), ProductFeatures.PURCHASES_CREATE);
        menuButtonSetting.configureButton(btnQuickPurchase, getTotalPurchase().addQuickInvoice(), ProductFeatures.PURCHASES_CREATE);
        menuButtonSetting.configureButton(btnTotalPurchase, getTotalPurchase().totals(), ProductFeatures.PURCHASES_LIST);
        menuButtonSetting.configureButton(btnPurchaseRe, getTotalPurchaseReturn().addInvoice(), ProductFeatures.PURCHASES_RETURN_CREATE);
        menuButtonSetting.configureButton(btnTotalPurchaseRe, getTotalPurchaseReturn().totals(), ProductFeatures.PURCHASES_RETURN_LIST);
        /*----------------------------------------------- Items -----------------------------------------------*/
        menuButtonSetting.configureButton(btnItems, getItemsButtons().allItems(), ProductFeatures.ITEMS_LIST);
        menuButtonSetting.configureButton(btnItemGroups, getItemsButtons().itemGroupManager(), ProductFeatures.ITEMS_GROUPS);
        menuButtonSetting.configureButton(btnAddItem, getItemsButtons().addItem(), ProductFeatures.ITEMS_ADD);
        // Disabled, not hidden: every other command in this sidebar greys out when its
        // permission is missing, and one button that vanishes instead makes the list a
        // different length for different users - which is what a shop owner describing a
        // screen over the phone has to reason about. configureButton already does it, and
        // MasterDataButton answers PermissionKey.deny() when no section is visible.
        menuButtonSetting.configureButton(btnMasterData, getItemsButtons().masterData(), ProductFeatures.ITEMS_MASTER_DATA);
        menuButtonSetting.configureButton(btnInventory, getItemsButtons().inventory(), ProductFeatures.ITEMS_INVENTORY);
        menuButtonSetting.configureButton(btnStockCount, getItemsButtons().stockCount(), ProductFeatures.ITEMS_STOCK_COUNT);
        menuButtonSetting.configureButton(btnStocks, getItemsButtons().stocks(), ProductFeatures.ITEMS_STOCKS);
        menuButtonSetting.configureButton(btnStockTransfers, getItemsButtons().stockTransfers(), ProductFeatures.ITEMS_STOCK_TRANSFERS);
        menuButtonSetting.configureButton(btnMergeItems, getItemsButtons().mergeItems(), ProductFeatures.ITEMS_MERGE);
        menuButtonSetting.configureButton(btnPriceCheck, getItemsButtons().priceCheck(), ProductFeatures.ITEMS_PRICE_CHECK);
        /*----------------------------------------------- Custom -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddCustomerName, getNameCustomer().addName(), ProductFeatures.CUSTOMERS_ADD);
        menuButtonSetting.configureButton(btnCustomer, getNameCustomer().namesData(), ProductFeatures.CUSTOMERS_LIST);
        menuButtonSetting.configureButton(btnAccountCustom, getAccountButtonsCustom(), ProductFeatures.CUSTOMERS_ACCOUNT);
        /*----------------------------------------------- Suppliers -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddSupplierName, getNameSup().addName(), ProductFeatures.SUPPLIERS_ADD);
        menuButtonSetting.configureButton(btnSuppliers, getNameSup().namesData(), ProductFeatures.SUPPLIERS_LIST);
        menuButtonSetting.configureButton(btnAccountSuppliers, getAccountButtonsSup(), ProductFeatures.SUPPLIERS_ACCOUNT);
        /*----------------------------------------------- Employees -----------------------------------------------*/
        menuButtonSetting.configureButton(btnAddEmployee, getAddEmployee().addEmployee(), ProductFeatures.EMPLOYEES_ADD);
        menuButtonSetting.configureButton(btnEmployees, getAddEmployee().employees(), ProductFeatures.EMPLOYEES_LIST);
        menuButtonSetting.configureButton(btnAddUser, getUsersAll().getUsers_add(), ProductFeatures.USERS_ADD);
        menuButtonSetting.configureButton(btnUsers, getUsersAll().getUsers_all(), ProductFeatures.USERS_LIST);
        /*----------------------------------------------- Treasury -----------------------------------------------*/
        menuButtonSetting.configureButton(btnTreasuries, getTreasuryButtons().treasuries(), ProductFeatures.TREASURY_LIST);
        // No product feature of its own: a key new to the catalogue is absent from every profile already
        // signed, and would take the screen from every shop that has one. The treasuries' own feature
        // stands for it - a currency is what the tills count in.
        menuButtonSetting.configureButton(btnCurrencies, getTreasuryButtons().currencies(), ProductFeatures.TREASURY_LIST);
        menuButtonSetting.configureButton(btnTreasuryCash, getTreasuryButtons().treasuryCash(), ProductFeatures.TREASURY_CASH);
        menuButtonSetting.configureButton(btnTreasuryTransfer, getTreasuryButtons().treasuryTransfer(), ProductFeatures.TREASURY_TRANSFER);
        menuButtonSetting.configureButton(btnTreasuryCapital, getTreasuryButtons().treasuryCapital(), ProductFeatures.TREASURY_CAPITAL);
        menuButtonSetting.configureButton(btnTreasuryDetails, getTreasuryButtons().treasuryDetails(), ProductFeatures.TREASURY_DETAILS);
        menuButtonSetting.configureButton(btnProcess, getTreasuryButtons().openProcess(), ProductFeatures.TREASURY_AUDIT);
        menuButtonSetting.configureButton(btnExpenses, getTreasuryButtons().openExpenses(), ProductFeatures.TREASURY_EXPENSES);
        /*----------------------------------------------- Reports -----------------------------------------------*/
        // No product feature of its own, on purpose: the hub opens nothing itself and every card asks
        // for its report's feature before opening it. A key new to the catalogue would be missing
        // from every profile already signed, and would take the hub away from each of those shops.
        menuButtonSetting.configureButton(btnReportHub, getReportsButtons().reportsHub());
        menuButtonSetting.configureButton(btnReportSummary, getReportsButtons().summaryReport(), ProductFeatures.REPORT_SUMMARY);
        // One screen for the item movement ranking and the daily item sales: shown while the edition
        // carries either, as the two buttons it replaced each were.
        menuButtonSetting.configureButton(btnReportItemSales, getReportsButtons().itemSales(null), ProductFeatures.REPORT_ITEMS, ProductFeatures.REPORT_ITEMS_DAILY);
        // One screen for sales and purchases since 2026-09-23, and so one button that either side's feature opens.
        menuButtonSetting.configureButton(btnReportMonthlyTotals, getReportsButtons().monthlyTotals(null), ProductFeatures.REPORT_SALES_YEAR, ProductFeatures.REPORT_PURCHASES_YEAR);
        // One screen for both sides since 2026-09-23, and so one button that either side's feature opens.
        menuButtonSetting.configureButton(btnReportPartyPayments, getReportsButtons().partyPayments(null), ProductFeatures.REPORT_CUSTOMER_PAYMENTS, ProductFeatures.REPORT_SUPPLIER_PAYMENTS);
        menuButtonSetting.configureButton(btnReportYearly, getReportsButtons().reportYearly(), ProductFeatures.REPORT_YEARLY);
        menuButtonSetting.configureButton(btnReportProfitLoss, getReportsButtons().profitLossReport(), ProductFeatures.REPORT_PROFIT_LOSS);
        menuButtonSetting.configureButton(btnReportReturnReasons, getReportsButtons().returnReasonsReport(), ProductFeatures.REPORT_RETURN_REASONS);
        /*----------------------------------------------- Setting -----------------------------------------------*/
        menuButtonSetting.configureButton(btnHome, getSettingButtons().home());
        menuButtonSetting.configureButton(btnSetting, getSettingButtons().setting(), ProductFeatures.SYSTEM_SETTINGS);
        menuButtonSetting.configureButton(btnMyShift, getShiftButtons().openShiftScreen(), ProductFeatures.SYSTEM_MY_SHIFT);
        menuButtonSetting.configureButton(btnShiftReports, getSettingButtons().adminShifts(), ProductFeatures.SYSTEM_SHIFT_REPORTS);
        menuButtonSetting.configureButton(btnBackup, getSettingButtons().backup(), ProductFeatures.SYSTEM_BACKUP);
        menuButtonSetting.configureButton(btnDeleteData, getSettingButtons().deleteData(), ProductFeatures.SYSTEM_DELETE_DATA);
        menuButtonSetting.configureButton(btnAbout, getSettingButtons().about());
        menuButtonSetting.configureButton(btnClose, getSettingButtons().close());
        /*----------------------------------------------- User menu (moved from the removed top toolbar) -----------------------------------------------*/
        menuButtonSetting.initializeMenuItem(menuItemChangeName, getForAllButtons().changeName());
        menuButtonSetting.initializeMenuItem(menuItemChangePass, getForAllButtons().changePassword());
        menuButtonSetting.initializeMenuItem(menuItemLogout, getForAllButtons().logout());
        // Every signed-in user may log out, regardless of what other permissions they hold.
        menuItemLogout.setDisable(false);

        applySectionVisibility();
        sidebarReady = true;
        configureSidebarShortcuts();
    }

    /**
     * A section is shown when the edition carries it and at least one of its commands opens for this
     * user; inside a shown section a command the user may not use stays visible and disabled.
     * <p>
     * Two sections used to be hidden by a single key instead - employees by {@code employee.show},
     * settings by {@code setting.show} - and that was wrong both ways. The settings section holds the
     * cashier's own shift screen, which has a key of its own, so a user given the shift screen and not
     * the settings could not reach it; and a user given the settings could reach everything in the
     * section, the delete-data screen included. The users list was unreachable the same way for
     * anybody holding {@code users.show} without {@code employee.show}.
     * <p>
     * <b>A hidden section leaves the accordion's list; it is not flagged invisible.</b> The accordion's
     * skin lays out every pane in its list and asks neither {@code visible} nor {@code managed}, so a
     * section made invisible left an empty slot the height of its header - seen on screen, signed in as
     * a cashier, with four such slots down the sidebar. The list is rebuilt from the FXML's order each
     * time, so a section that comes back returns to its place.
     * <p>
     * The settings section always shows: home, about and close are open to everybody.
     */
    private void applySectionVisibility() {
        if (allSections == null) {
            allSections = List.copyOf(sideAccordion.getPanes());
        }
        // No category for the settings section: it also holds shell commands (Home, About and Close),
        // which are deliberately always available even when every configurable system screen is absent.
        Map<TitledPane, String> categories = Map.of(
                paneSales, ProductFeatures.CATEGORY_SALES,
                panePurchase, ProductFeatures.CATEGORY_PURCHASES,
                paneItems, ProductFeatures.CATEGORY_ITEMS,
                paneCustom, ProductFeatures.CATEGORY_CUSTOMERS,
                paneSuppliers, ProductFeatures.CATEGORY_SUPPLIERS,
                paneEmployees, ProductFeatures.CATEGORY_TEAM,
                paneTreasury, ProductFeatures.CATEGORY_TREASURY,
                paneReports, ProductFeatures.CATEGORY_REPORTS);
        List<TitledPane> shown = allSections.stream()
                .filter(section -> inEdition(categories.get(section)) && opensSomething(section))
                .toList();
        allSections.stream().filter(section -> !shown.contains(section)).forEach(section -> section.setExpanded(false));
        if (!sideAccordion.getPanes().equals(shown)) {
            sideAccordion.getPanes().setAll(shown);
        }
    }

    private boolean inEdition(String categoryKey) {
        return categoryKey == null || (productFeatures != null
                && ProductFeatures.keysInCategory(categoryKey).stream().anyMatch(productFeatures::isEnabled));
    }

    /** Whether any command in the section is on screen and enabled. */
    private static boolean opensSomething(TitledPane section) {
        return section.getContent() instanceof Pane box && box.getChildren().stream()
                .anyMatch(node -> node instanceof Button button
                        && button.isVisible() && button.isManaged() && !button.isDisable());
    }

    private void setupShiftPolicyVisibility() {
        refreshShiftButtonVisibility();
        subscriptions.add(eventBus.subscribe(ShiftPolicyChanged.class, event -> refreshShiftButtonVisibility()));
    }

    /**
     * The shift button answers to two rules - the edition carries the feature, and the shop
     * has shifts switched on - and the second one changes while the program is running.
     * <p>
     * Re-installing the shortcuts on a change is what keeps the two halves of that agreeing.
     * {@link #configureSidebarShortcuts()} only offers a key to a button that is visible, and
     * it runs once, during setup, when shifts are still {@code DISABLED} on most installs. So
     * a shop that switched shifts on had the button appear with its key dead until the next
     * restart. {@code SidebarShortcutManager.install} clears and rebinds, so calling it again
     * is safe.
     */
    private void refreshShiftButtonVisibility() {
        boolean wasVisible = btnMyShift.isVisible();
        try {
            boolean enabled = productFeatures != null
                    && productFeatures.isEnabled(ProductFeatures.SYSTEM_MY_SHIFT)
                    && ServiceRegistry.get(ShiftPolicyService.class).current().mode() != ShiftMode.DISABLED;
            btnMyShift.setVisible(enabled);
            btnMyShift.setManaged(enabled);
        } catch (DaoException e) {
            btnMyShift.setVisible(false);
            btnMyShift.setManaged(false);
        }
        // Not before the sidebar has been built: the first call to this method comes from
        // setupRightPane, ahead of configureAllButtons, and a shortcut map taken then would
        // describe the FXML defaults rather than the edition. That call needs nothing -
        // configureAllButtons installs the real map moments later.
        if (sidebarReady && btnMyShift.isVisible() != wasVisible) {
            applySectionVisibility();
            configureSidebarShortcuts();
        }
    }

    private void configureSidebarShortcuts() {
        Map<SidebarShortcut, Button> allShortcuts = sidebarCommands();
        Map<SidebarShortcut, Button> shortcuts = new EnumMap<>(SidebarShortcut.class);
        allShortcuts.forEach((shortcut, button) -> {
            if (button.isVisible() && button.isManaged()) shortcuts.put(shortcut, button);
        });
        if (mainContentBox.getScene() != null) {
            SidebarShortcutManager.install(mainContentBox.getScene(), shortcuts);
        } else {
            mainContentBox.sceneProperty().addListener((observable, oldScene, scene) -> {
                if (scene != null) SidebarShortcutManager.install(scene, shortcuts);
            });
        }
    }

    /**
     * Every sidebar command by the stable id it is known to the rest of the system by.
     * <p>
     * It was a local variable inside {@link #configureSidebarShortcuts()} until the user manual's
     * screenshot harness needed to open each screen the way a user does. Copying it there would
     * have been a second list of every screen in the program, kept in step by hope; a screen added
     * to one and not the other is a screen whose picture is silently never taken.
     */
    public Map<SidebarShortcut, Button> sidebarCommands() {
        return Map.ofEntries(
                Map.entry(SidebarShortcut.SALES, btnSales), Map.entry(SidebarShortcut.SALES_RETURN, btnSalesReturn), Map.entry(SidebarShortcut.TOTAL_SALES, btnTotalSale), Map.entry(SidebarShortcut.TOTAL_SALES_RETURN, btnTotalSalesReturn),
                Map.entry(SidebarShortcut.PURCHASE, btnPurchase), Map.entry(SidebarShortcut.PURCHASE_RETURN, btnPurchaseRe), Map.entry(SidebarShortcut.TOTAL_PURCHASE, btnTotalPurchase), Map.entry(SidebarShortcut.TOTAL_PURCHASE_RETURN, btnTotalPurchaseRe),
                Map.entry(SidebarShortcut.ITEMS, btnItems), Map.entry(SidebarShortcut.ITEM_GROUPS, btnItemGroups), Map.entry(SidebarShortcut.ADD_ITEM, btnAddItem), Map.entry(SidebarShortcut.MASTER_DATA, btnMasterData), Map.entry(SidebarShortcut.INVENTORY, btnInventory), Map.entry(SidebarShortcut.STOCK_COUNT, btnStockCount), Map.entry(SidebarShortcut.STOCKS, btnStocks), Map.entry(SidebarShortcut.STOCK_TRANSFERS, btnStockTransfers), Map.entry(SidebarShortcut.MERGE_ITEMS, btnMergeItems), Map.entry(SidebarShortcut.PRICE_CHECK, btnPriceCheck),
                Map.entry(SidebarShortcut.ADD_CUSTOMER, btnAddCustomerName), Map.entry(SidebarShortcut.CUSTOMERS, btnCustomer), Map.entry(SidebarShortcut.CUSTOMER_ACCOUNT, btnAccountCustom),
                Map.entry(SidebarShortcut.ADD_SUPPLIER, btnAddSupplierName), Map.entry(SidebarShortcut.SUPPLIERS, btnSuppliers), Map.entry(SidebarShortcut.SUPPLIER_ACCOUNT, btnAccountSuppliers),
                Map.entry(SidebarShortcut.ADD_EMPLOYEE, btnAddEmployee), Map.entry(SidebarShortcut.EMPLOYEES, btnEmployees), Map.entry(SidebarShortcut.ADD_USER, btnAddUser), Map.entry(SidebarShortcut.USERS, btnUsers),
                Map.entry(SidebarShortcut.TREASURIES, btnTreasuries), Map.entry(SidebarShortcut.CURRENCIES, btnCurrencies), Map.entry(SidebarShortcut.TREASURY_CASH, btnTreasuryCash), Map.entry(SidebarShortcut.TREASURY_TRANSFER, btnTreasuryTransfer), Map.entry(SidebarShortcut.TREASURY_CAPITAL, btnTreasuryCapital), Map.entry(SidebarShortcut.TREASURY_DETAILS, btnTreasuryDetails), Map.entry(SidebarShortcut.TREASURY_PROCESS, btnProcess), Map.entry(SidebarShortcut.EXPENSES, btnExpenses),
                Map.entry(SidebarShortcut.REPORT_HUB, btnReportHub), Map.entry(SidebarShortcut.REPORT_SUMMARY, btnReportSummary), Map.entry(SidebarShortcut.REPORT_ITEM_SALES, btnReportItemSales), Map.entry(SidebarShortcut.REPORT_MONTHLY_TOTALS, btnReportMonthlyTotals), Map.entry(SidebarShortcut.REPORT_PARTY_PAYMENTS, btnReportPartyPayments), Map.entry(SidebarShortcut.REPORT_YEARLY, btnReportYearly), Map.entry(SidebarShortcut.REPORT_PROFIT_LOSS, btnReportProfitLoss), Map.entry(SidebarShortcut.REPORT_RETURN_REASONS, btnReportReturnReasons),
                Map.entry(SidebarShortcut.HOME, btnHome), Map.entry(SidebarShortcut.SETTINGS, btnSetting), Map.entry(SidebarShortcut.SHIFT_REPORTS, btnShiftReports), Map.entry(SidebarShortcut.BACKUP, btnBackup), Map.entry(SidebarShortcut.DELETE_DATA, btnDeleteData), Map.entry(SidebarShortcut.ABOUT, btnAbout), Map.entry(SidebarShortcut.CLOSE, btnClose), Map.entry(SidebarShortcut.YOUTUBE, btnYouTube));
    }

    private void rightPaneSetting() {
        var lm = LanguageManager.getInstance();
        titlePaneSetting(paneSales, lm.getString("sales"), AppIcon.SALES);
        titlePaneSetting(panePurchase, lm.getString("pur"), AppIcon.PURCHASE);
        titlePaneSetting(paneItems, lm.getString("items"), AppIcon.ITEM);
        titlePaneSetting(paneCustom, lm.getString("customers"), AppIcon.CUSTOMERS);
        titlePaneSetting(paneSuppliers, lm.getString("suppliers"), AppIcon.SUPPLIERS);
        titlePaneSetting(paneEmployees, lm.getString("employees"), AppIcon.EMPLOYEES);
        titlePaneSetting(paneTreasury, lm.getString("treasury.label.treasury"), AppIcon.TREASURY_CASH);
        titlePaneSetting(paneReports, lm.getString("report"), AppIcon.REPORT);
        titlePaneSetting(paneSetting, lm.getString("menu.settings"), AppIcon.SETTINGS);

        // Fixed bilingual brand identity, not translatable UI text - same choice
        // made for SettingApplication's stage title.
        txtNameProject.setText(PROGRAM_TITLE);
        txtName.setText(PROGRAM_NAME_EN);
        txtTel.setText(PROGRAM_TEL);
    }

    private void titlePaneSetting(TitledPane titledPane, String text, AppIcon icon) {
        titledPane.setText(text);
        titledPane.setContentDisplay(LanguageManager.getInstance().isRtl()
                ? ContentDisplay.RIGHT : ContentDisplay.LEFT);
        titledPane.setGraphic(icon.graphic(20));
    }

    /**
     * The sidebar on a short screen.
     *
     * <p>The menu is nine accordion headers, a brand row, a user menu and a footer inside a
     * fixed-height column. On a 1366x768 screen the window is about 730 points tall, the
     * chrome takes some 560 of them, and the expanded section was left with a sliver: the
     * items list showed <em>one</em> of its ten buttons, with a scroll arrow above and below
     * it. Nothing was broken and nothing said so - the pane simply shrank to what was left.
     *
     * <p>Two things answer it, and the first is the one that matters. The accordion and the
     * footer now live inside one {@code ScrollPane}, so a section that does not fit scrolls
     * <em>with its headers</em> instead of being compressed - what a menu taller than its
     * window has to do. The second is density: under {@link #COMPACT_SIDEBAR_HEIGHT} the
     * paddings, the spacing and the logo shrink, which on the measured screen is worth about
     * a hundred points and usually removes the need to scroll at all.
     *
     * <p>Spacing is set here rather than in the stylesheet because the FXML sets it
     * explicitly, and a value set on the node wins over CSS.
     */
    private void setupResponsiveSidebar() {
        borderPane.heightProperty().addListener(
                (observable, was, now) -> applySidebarDensity(now.doubleValue()));
        applySidebarDensity(borderPane.getHeight());

        // Expanding a section that sits below the fold would otherwise open out of sight.
        sideAccordion.expandedPaneProperty().addListener((observable, was, expanded) -> {
            if (expanded != null) {
                Platform.runLater(() -> scrollSideMenuTo(expanded));
            }
        });
    }

    private void applySidebarDensity(double height) {
        boolean compact = height > 0 && height < COMPACT_SIDEBAR_HEIGHT;

        rightPaneRoot.getStyleClass().remove("sidebar-compact");
        if (compact) {
            rightPaneRoot.getStyleClass().add("sidebar-compact");
        }

        double gap = compact ? 6 : 12;
        sidebarStack.setSpacing(gap);
        sideMenuBox.setSpacing(gap);

        double logo = compact ? 34 : 60;
        imgCompanyLogo.setFitHeight(logo);
        imgCompanyLogo.setFitWidth(logo);
    }

    /** Puts the expanded section's header at the top of the viewport. */
    private void scrollSideMenuTo(Node node) {
        Node content = sideMenuScroll.getContent();
        if (content == null) {
            return;
        }
        double contentHeight = content.getBoundsInLocal().getHeight();
        double viewport = sideMenuScroll.getViewportBounds().getHeight();
        if (contentHeight <= viewport) {
            return;
        }
        double top = content.sceneToLocal(node.localToScene(0, 0)).getY();
        double value = top / (contentHeight - viewport);
        sideMenuScroll.setVvalue(Math.max(0, Math.min(1, value)));
    }

    /** Keeps sidebar text and category icons aligned to the active reading direction. */
    private void applySidebarDirection() {
        boolean rtl = LanguageManager.getInstance().isRtl();
        rightPaneRoot.setNodeOrientation(LanguageManager.getInstance().getNodeOrientation());
        rightPaneRoot.getStyleClass().removeAll("sidebar-rtl", "sidebar-ltr");
        rightPaneRoot.getStyleClass().add(rtl ? "sidebar-rtl" : "sidebar-ltr");
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
        Image image = logo == null ? new Image(java.util.Objects.requireNonNull(
                com.hamza.account.Main.class.getResource("image/default-blog.png")).toExternalForm()) : logo.toFxImage();
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
        lblUserRole.setText(CurrentUser.isSystemAdministrator()
                ? lm.getString("nav.user.role.admin") : lm.getString("nav.user.role.user"));
        lblUserInitial.setText(username == null || username.isBlank() ? "?" : username.substring(0, 1).toUpperCase());
    }

    // ------------------------------------------------------------------
    // Notifications + YouTube (moved here from the removed top toolbar)
    // ------------------------------------------------------------------

    private void setupNotificationBell() {
        notificationBellSlot.getChildren().setAll(NotificationBootstrap.start().createBell());
    }

    private void setupYouTube() {
        btnYouTube.setGraphic(AppIcon.VIDEO_HELP.graphic(20));
        btnYouTube.setText(LanguageManager.getInstance().getString("nav.youtube.explain"));
        btnYouTube.setTooltip(new Tooltip(LanguageManager.getInstance().getString("nav.youtube.tooltip")));
        btnYouTube.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(new URI("https://www.youtube.com/playlist?list=PL2fs9t9FGXhoSOJ5UFsAWm2tLS_EfOvAE"));
            } catch (Exception ex) {
                logException(ex);
            }
        });

        boolean isAdmin = CurrentUser.isSystemAdministrator();
        btnYouTube.setVisible(isAdmin);
        btnYouTube.setManaged(isAdmin);
    }

    private void firstBoxInMain() {
        box.getChildren().clear();
        box.getChildren().add(new ModernDashboardApp(ModernDashboardApp.roads(this, menuButtonSetting.tabPane())).getPane());
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
