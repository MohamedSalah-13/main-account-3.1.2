package com.hamza.account.controller.setting;

import com.hamza.account.Main;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.LanguageChanged;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPaneIndex;
import static com.hamza.account.config.PropertiesName.setPaneIndex;


@FxmlPath(pathFile = "setting-view.fxml")
public class SettingController implements Initializable, AppSettingInterface {

    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();
    private SettingCompanyController companyController;

    @FXML
    private TabPane pane;
    @FXML
    private VBox box;

    public SettingController(DaoFactory daoFactory
            , DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(LanguageChanged.class, event -> reloadTabsForLanguage()));
            subscriptions.disposeWith(box);
        }
    }

    private void otherSetting() {

        try {
            addTabs();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.loadContext"), e);
        }

        int paneShow = getPaneIndex();
        pane.getSelectionModel().select(paneShow);
        pane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, node, t1) -> {
                    setPaneIndex(t1.intValue());
                });

    }

    private void addTabs() throws Exception {
        // Keep the three declarative tabs and replace their content and optional tabs.
        // FXML resolves %keys only when it is loaded, so replacing the content is what makes a
        // language switch visible immediately instead of asking the user to close Settings.
        while (pane.getTabs().size() > 3) {
            pane.getTabs().removeLast();
        }
        var lm = LanguageManager.getInstance();
        // tab company
        Tab tabCompany = pane.getTabs().getFirst();
        // Keep unsaved company edits when the language changes within this same tab.
        if (companyController == null || !companyController.hasUnsavedChanges()) {
            tabCompany.setContent(getTabCompany());
        } else {
            companyController.refreshLanguage();
        }
        tabCompany.setText(lm.getString("settings.company.tabTitle"));
        // Refresh immediate preferences even when the company form has unsaved edits.
        companyController.setSystemSettings(getTabLanguage());
        // tab barcode
        Tab tabBarcode = pane.getTabs().get(1);
        tabBarcode.setContent(getTabBarcode());
        tabBarcode.setText(lm.getString("items"));
        // tab checks
        Tab tabChecks = pane.getTabs().get(2);
        tabChecks.setContent(getTabChecks());
        tabChecks.setText(lm.getString("show"));
        pane.getTabs().add(new Tab(lm.getString("settings.printers.tabTitle"), getTabPrinters()));
        pane.getTabs().add(getTabReportStyle());
        pane.getTabs().add(new Tab(lm.getString("settings.shortcuts.tabTitle"), getTabShortcuts()));
        // add tab notifications
        pane.getTabs().add(new Tab(lm.getString("settings.notifications.tabTitle"), getTabNotifications()));
        // add tab accounting period
        pane.getTabs().add(new Tab(lm.getString("settings.periodLock.tabTitle"), getTabPeriodLock()));
        // The backup tab is its own ability, like every other screen that can take the
        // database out of the building. A user without it loses the tab, not its buttons.
        if (AuthorizationGuard.isGranted(AppPermissions.SETTING_BACKUP_SHOW)) {
            pane.getTabs().add(new Tab(lm.getString("backup"), backup()));
            pane.getTabs().add(new Tab(lm.getString("workstations.tabTitle"), getTabWorkstations()));
        }
    }

    private void reloadTabsForLanguage() {
        int selectedIndex = pane.getSelectionModel().getSelectedIndex();
        try {
            addTabs();
            pane.getSelectionModel().select(Math.max(0, Math.min(selectedIndex, pane.getTabs().size() - 1)));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("settings.loadContext"), e);
        }
    }

    private Pane getTabWorkstations() throws Exception {
        return new OpenFxmlApplication(new WorkstationsController()).getPane();
    }

    private Pane getTabShortcuts() throws Exception {
        return new OpenFxmlApplication(new SettingTabShortcutsController()).getPane();
    }

    private Pane getTabNotifications() throws Exception {
        return new OpenFxmlApplication(new SettingTabNotificationsController()).getPane();
    }

    private Pane getTabPeriodLock() throws Exception {
        return new OpenFxmlApplication(new SettingTabPeriodLockController()).getPane();
    }

    private Pane getTabCompany() throws IOException {
        // Its collaborators come from ServiceRegistry, so it needs nothing handed to it.
        companyController = new SettingCompanyController();
        return new OpenFxmlApplication(companyController).getPane();
    }

    private Pane getTabLanguage() throws Exception {
        SettingTabLanguageController languageController = new SettingTabLanguageController();
        return new OpenFxmlApplication(languageController).getPane();
    }

    private Pane getTabBarcode() throws Exception {
        SettingTabBarcodeController barcodeController =
                new SettingTabBarcodeController(daoFactory);
        return new OpenFxmlApplication(barcodeController).getPane();
    }

    private Pane getTabChecks() throws Exception {
        SettingTabCheckController checkController = new SettingTabCheckController(dataPublisher);
        return new OpenFxmlApplication(checkController).getPane();
    }

    private Pane getTabPrinters() throws Exception {
        return new OpenFxmlApplication(new SettingTabPrintersController()).getPane();
    }

    /** Built in code; its preview is drawn the first time the tab is chosen, not whenever settings opens. */
    private Tab getTabReportStyle() {
        SettingTabReportStyleController controller = new SettingTabReportStyleController();
        Tab tab = new Tab(LanguageManager.getInstance().getString("report.style.tabTitle"), controller.build());
        tab.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) {
                controller.shown();
            }
        });
        return tab;
    }

    private Parent backup() throws IOException {
        // With a bundle, like every other screen. Without one the loader throws on the
        // first %key, which is why this tab's text was Arabic literals in the FXML and
        // stayed Arabic in the English build.
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/BackupView.fxml"),
                LanguageManager.getInstance().getResourceBundle());
        Parent root = loader.load();
        com.hamza.account.backup.BackupController controller = loader.getController();
        var connection = new ConnectionToDatabase();
        controller.initConnection(connection.getHost(), connection.getPort(), connection.getDbName()
                , connection.getUsername(), connection.getPass());
        return root;
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return LanguageManager.getInstance().getString("settings.title");
    }

    @Override
    public boolean resize() {
        return true;
    }
}
