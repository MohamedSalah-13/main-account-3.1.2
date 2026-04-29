package com.hamza.account.controller.setting;

import com.hamza.account.Main;
import com.hamza.account.config.ConnectionToMysql;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPaneIndex;
import static com.hamza.account.config.PropertiesName.setPaneIndex;
import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;


@Log4j2
@FxmlPath(pathFile = "setting-view.fxml")
public class SettingController extends ServiceData implements Initializable, AppSettingInterface {

    private final DataPublisher dataPublisher;
    private final DaoFactory daoFactory;
    private final LoadDataAndList loadDataAndList;
    @FXML
    private TabPane pane;
    @FXML
    private VBox box;

    public SettingController(DaoFactory daoFactory
            , DataPublisher dataPublisher, LoadDataAndList loadDataAndList) throws Exception {
        super(daoFactory);
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
        this.loadDataAndList = loadDataAndList;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
    }

    private void otherSetting() {

        try {
            addTabs();
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }

        int paneShow = getPaneIndex();
        pane.getSelectionModel().select(paneShow);
        pane.getSelectionModel().selectedIndexProperty()
                .addListener((observableValue, node, t1) -> {
                    setPaneIndex(t1.intValue());
                });

    }

    private void addTabs() throws Exception {
        // tab company
        Tab tabCompany = pane.getTabs().getFirst();
        tabCompany.setContent(getTabCompany());
        tabCompany.setText(Setting_Language.COMPANY_DATA);
        // tab language
        Tab tabLanguage = pane.getTabs().get(1);
        tabLanguage.setContent(getTabLanguage());
        tabLanguage.setText(Setting_Language.OTHERS);
        // tab barcode
        Tab tabBarcode = pane.getTabs().get(2);
        tabBarcode.setContent(getTabBarcode());
        tabBarcode.setText(Setting_Language.WORD_ITEMS);
        // tab checks
        Tab tabChecks = pane.getTabs().get(3);
        tabChecks.setContent(getTabChecks());
        tabChecks.setText(Setting_Language.WORD_SHOW);
        // add tab backup
//        Pane backupAppPane = backupSetting();
        pane.getTabs().add(new Tab(Setting_Language.WORD_BACKUP, backup()));
    }

    private Pane getTabCompany() throws IOException {
        SettingCompanyController companyController = new SettingCompanyController(daoFactory, dataPublisher, this);
        return new OpenFxmlApplication(companyController).getPane();
    }

    private Pane getTabLanguage() throws Exception {
        SettingTabLanguageController languageController = new SettingTabLanguageController(daoFactory, dataPublisher);
        return new OpenFxmlApplication(languageController).getPane();
    }

    private Pane getTabBarcode() throws Exception {
        SettingTabBarcodeController barcodeController =
                new SettingTabBarcodeController(daoFactory, this, dataPublisher.getPublisherSelPriceUnits());
        return new OpenFxmlApplication(barcodeController).getPane();
    }

    private Pane getTabChecks() throws Exception {
        SettingTabCheckController checkController = new SettingTabCheckController(dataPublisher);
        return new OpenFxmlApplication(checkController).getPane();
    }

    private Pane backupSetting() throws Exception {
        Task<Void> voidTask = new Task<>() {
            @Override
            protected Void call() {
                double maxLength = 100;
                for (int i = 1; i <= maxLength; i++) {
                    try {
                        int sum = (int) roundToTwoDecimalPlaces((i / maxLength) * 100);
                        updateProgress(i, maxLength);
                        updateMessage(String.valueOf(sum));
                        updateTitle("Load Data " + i);
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage(), e.getCause());
                    }
                }
                return null;
            }
        };

        BackupController<Void> controller = new BackupController<>(new ConnectionToMysql().connect()
                , workerStateEvent -> {
            loadDataAndList.updateData(dataPublisher);
            AllAlerts.alertSave();
        }, voidTask);
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("view/backup-view.fxml"));
        fxmlLoader.setController(controller);
        return fxmlLoader.load();
    }

    private Parent backup() throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("view/BackupView.fxml"));
        Parent root = loader.load();
        com.hamza.account.backup.BackupController controller = loader.getController();
        controller.initConnection("localhost", "3306", "account_system_db", "root", "m13ido");
        return root;
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return Setting_Language.WORD_SETTING;
    }

    @Override
    public boolean resize() {
        return true;
    }
}
