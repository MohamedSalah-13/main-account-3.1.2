package com.hamza.account.controller.setting;

import com.hamza.account.Main;
import com.hamza.account.config.ConnectionToDatabase;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
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
        var lm = LanguageManager.getInstance();
        // tab company
        Tab tabCompany = pane.getTabs().getFirst();
        tabCompany.setContent(getTabCompany());
        tabCompany.setText(lm.getString("settings.company.tabTitle"));
        // tab language
        Tab tabLanguage = pane.getTabs().get(1);
        tabLanguage.setContent(getTabLanguage());
        tabLanguage.setText(lm.getString("others"));
        // tab barcode
        Tab tabBarcode = pane.getTabs().get(2);
        tabBarcode.setContent(getTabBarcode());
        tabBarcode.setText(lm.getString("items"));
        // tab checks
        Tab tabChecks = pane.getTabs().get(3);
        tabChecks.setContent(getTabChecks());
        tabChecks.setText(lm.getString("show"));
        pane.getTabs().add(new Tab(lm.getString("settings.printers.tabTitle"), getTabPrinters()));
        pane.getTabs().add(new Tab(lm.getString("settings.shortcuts.tabTitle"), getTabShortcuts()));
        // add tab notifications
        pane.getTabs().add(new Tab(lm.getString("settings.notifications.tabTitle"), getTabNotifications()));
        // add tab accounting period
        pane.getTabs().add(new Tab(lm.getString("settings.periodLock.tabTitle"), getTabPeriodLock()));
        // add tab backup
//        Pane backupAppPane = backupSetting();
        pane.getTabs().add(new Tab(lm.getString("backup"), backup()));
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
        return new OpenFxmlApplication(new SettingCompanyController()).getPane();
    }

    private Pane getTabLanguage() throws Exception {
        SettingTabLanguageController languageController = new SettingTabLanguageController(dataPublisher);
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
