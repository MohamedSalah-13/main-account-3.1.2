package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.language.Setting_Language.CANCEL_SELECT_ALL;
import static com.hamza.controlsfx.language.Setting_Language.SELECT_ALL;

@Log4j2
@FxmlPath(pathFile = "delete-data.fxml")
public class DeleteDataController implements AppSettingInterface {

    private final DaoFactory daoFactory;
    private final DataPublisher dataPublisher;
    @FXML
    private CheckBox deleteSales, deleteSalesReturn, deleteCustomersAccount, deleteCustomers;
    @FXML
    private CheckBox deletePurchase, deletePurchaseReturn, deleteSuppliersAccount, deleteSuppliers;
    @FXML
    private CheckBox deleteItems, deleteStocks, deleteSubGroup, deleteMainGroup;
    @FXML
    private CheckBox deleteExpenses, deleteEmployees, deleteProcesses, deleteUsers;
    @FXML
    private Button btnSave, btnClose;
    @FXML
    private StackPane stackPane;
    @FXML
    private ToggleButton btnSelected;
    private MaskerPaneSetting maskerPaneSetting;

    public DeleteDataController(DaoFactory daoFactory, DataPublisher dataPublisher) {
        this.daoFactory = daoFactory;
        this.dataPublisher = dataPublisher;
    }

    @FXML
    public void initialize() {
        otherSetting();
        getData();
    }

    private void otherSetting() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnSelected.setText(SELECT_ALL);

        var images = new Image_Setting();
        btnClose.setGraphic(ImageChoose.createIcon(images.cancel));
        btnSave.setGraphic(ImageChoose.createIcon(images.save));
        btnSelected.setGraphic(ImageChoose.createIcon(images.select));
    }

    private void getData() {
        addActionForCheckBox(deleteSalesReturn, deleteSales, deletePurchaseReturn);
        addActionForCheckBox(deleteSales, deletePurchase, deleteCustomersAccount, deleteSuppliersAccount);
        addActionForCheckBox(deleteCustomersAccount, deleteCustomers, deleteExpenses);
        addActionForCheckBox(deletePurchase, deleteSuppliers, deleteItems);
        addActionForCheckBox(deleteItems, deleteStocks, deleteSubGroup);
        addActionForCheckBox(deleteSubGroup, deleteMainGroup);
        addActionForCheckBox(deleteExpenses, deleteEmployees, deleteProcesses, deleteUsers);

        btnSelected.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            btnSelected.setText(t1 ? CANCEL_SELECT_ALL : SELECT_ALL);

            checkSetting(btnSelected.isSelected(), deleteSales, deleteSalesReturn, deleteCustomersAccount, deleteCustomers, deletePurchase
                    , deletePurchaseReturn, deleteSuppliersAccount, deleteSuppliers
                    , deleteItems, deleteStocks, deleteSubGroup, deleteMainGroup, deleteExpenses, deleteEmployees, deleteProcesses, deleteUsers);
        });
        btnSave.setOnAction(actionEvent -> {
            if (!deleteSalesReturn.isSelected()) {
                deleteSalesReturn.requestFocus();
                AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
                return;
            }
            if (AllAlerts.confirmDelete()) {
                maskerPaneSetting.showMaskerPane(() -> delete());

                maskerPaneSetting.getVoidTask().setOnSucceeded(workerStateEvent -> {
                    AllAlerts.alertSave();
                    LoadDataAndList.updateData(dataPublisher);
                    btnSelected.setSelected(false);
                });
            }
        });

        btnClose.setOnAction(actionEvent -> ((Stage) btnClose.getScene().getWindow()).close());

    }

    private void addActionForCheckBox(CheckBox checkBoxMain, CheckBox... checkBoxes) {
        for (CheckBox box : checkBoxes) {
            box.disableProperty().bind(checkBoxMain.selectedProperty().not());
        }
        checkBoxMain.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            if (!t1) {
                for (CheckBox box : checkBoxes) {
                    box.setSelected(false);
                }
            }
        });
    }

    private void delete() {
        try {
            daoFactory.truncateDao().truncateTableSales(deleteSalesReturn.isSelected(), deleteSales.isSelected(), deleteCustomersAccount.isSelected(), deleteCustomers.isSelected());
            daoFactory.truncateDao().truncateTablePurchase(deletePurchaseReturn.isSelected(), deletePurchase.isSelected(), deleteSuppliersAccount.isSelected(), deleteSuppliers.isSelected());
            daoFactory.truncateDao().truncateTableItems(deleteItems.isSelected(), deleteStocks.isSelected(), deleteSubGroup.isSelected(), deleteMainGroup.isSelected());
            daoFactory.truncateDao().truncateTableOthers(deleteEmployees.isSelected(), deleteProcesses.isSelected(), deleteExpenses.isSelected(), deleteUsers.isSelected());

        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void checkSetting(boolean b, CheckBox... checkBoxes) {
        for (CheckBox box : checkBoxes) {
            box.setSelected(b);
        }
    }

    @Override
    public Pane pane() throws Exception {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return "Delete Tables";
    }
}
