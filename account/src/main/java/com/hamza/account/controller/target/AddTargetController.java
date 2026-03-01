package com.hamza.account.controller.target;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.base.BaseTarget;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Target;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.menu.ActionTable;
import com.hamza.controlsfx.menu.ContextMenuTable;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.AddColumnMix;
import com.hamza.controlsfx.table.ColumnInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static com.hamza.controlsfx.filechooser.ImageChoose.createIcon;
import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;

@Log4j2
@FxmlPath(pathFile = "target/addTarget-view.fxml")
public class AddTargetController extends ServiceData implements AppSettingInterface {

    private final Publisher<String> afterAddTarget;
    private final String textName;
    private TargetRateController targetRate1Controller, targetRate2Controller, targetRate3Controller;
    @FXML
    private TableView<Target> tableView;
    @FXML
    private ComboBox<String> searchableComboBox;
    @FXML
    private TextField txtCode, txtTarget, txtSearch;
    @FXML
    private Button btnSave, btnClear, btnClose;
    @FXML
    private Label labelCode, labelName, labelTarget, labelNotes, labelSearch;
    @FXML
    private TextArea notes;
    @FXML
    private GridPane gridPane;
    private StringProperty txtRate1, txtRate2, txtRate3;
    private StringProperty target_ratio1, target_ratio2, target_ratio3;

    public AddTargetController(DaoFactory daoFactory, Publisher<String> afterAddTarget, String textName) throws Exception {
        super(daoFactory);
        this.textName = textName;
        this.afterAddTarget = afterAddTarget;
    }

    @FXML
    public void initialize() {
        loadRateController();
        txtRate1 = targetRate1Controller.txtRatePropertyProperty();
        txtRate2 = targetRate2Controller.txtRatePropertyProperty();
        txtRate3 = targetRate3Controller.txtRatePropertyProperty();
        target_ratio1 = targetRate1Controller.targetRatePropertyProperty();
        target_ratio2 = targetRate2Controller.targetRatePropertyProperty();
        target_ratio3 = targetRate3Controller.targetRatePropertyProperty();
        getTable();
        otherSetting();
        getDelegate();
        action();
        buttonGraphic();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnSave.setGraphic(createIcon(images.save));
        btnClear.setGraphic(createIcon(images.erase));
        btnClose.setGraphic(createIcon(images.cancel));
    }

    private void loadRateController() {
        targetRate1Controller = new TargetRateController();
        targetRate2Controller = new TargetRateController();
        targetRate3Controller = new TargetRateController();
        gridPane.add(openRateController(targetRate1Controller), 1, 2);
        gridPane.add(openRateController(targetRate2Controller), 3, 2);
        gridPane.add(openRateController(targetRate3Controller), 1, 3);
    }

    private Pane openRateController(TargetRateController targetRateController) {
        try {
            return new OpenFxmlApplication(targetRateController).getPane();
        } catch (IOException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
            return new Pane();
        }
    }

    private void otherSetting() {
        Utils.setTextFormatter(txtTarget);
        labelCode.setText(Setting_Language.WORD_CODE);
        labelName.setText(Setting_Language.NAME_DELEGATE);
        labelTarget.setText(Setting_Language.TARGET);
        labelNotes.setText(Setting_Language.NOTES);
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        txtCode.setText(Setting_Language.generate);
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);
        btnClear.setText(Setting_Language.CLEAR);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        notes.setPromptText(Setting_Language.NOTES);
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, Target.class);
        refreshTable();
        tableView.setContextMenu(contextMenu());
//        new AddColumnRateController(tableView).addColumnRate();
    }

    private ContextMenu contextMenu() {
        return new ContextMenuTable(new ActionTable() {
            @Override
            public void actionAdd() {

            }

            @Override
            public void actionUpdate() {
                selectData();
            }

            @Override
            public void actionDelete() {
                deleteData();
            }

            @Override
            public void actionRefresh() {
                refreshTable();
            }
        });
    }

    private void getDelegate() {
        try {
            List<String> delegateNames = employeeService.getDelegateNames();
            searchableComboBox.setItems(FXCollections.observableArrayList(delegateNames));
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void action() {
        btnClear.setOnAction(actionEvent -> clearData());
        btnSave.setOnAction(actionEvent -> saveData());
        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
        txtSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, txtSearch.getText(), getList()));
        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) selectData();
        });

        tableView.setOnKeyPressed(event -> {
            if (event.getCode().equals(KeyCode.DELETE)) {
                deleteData();
            }
        });

        txtTarget.textProperty().addListener((observable, oldValue, newValue) -> {
            targetRate1Controller.setDoubleProperty(DoubleSetting.parseDoubleOrDefault(txtTarget.getText()));
            targetRate2Controller.setDoubleProperty(DoubleSetting.parseDoubleOrDefault(txtTarget.getText()));
            targetRate3Controller.setDoubleProperty(DoubleSetting.parseDoubleOrDefault(txtTarget.getText()));

        });

        btnClose.disableProperty().bind(tableView.selectionModelProperty().isNull());
        searchableComboBox.disableProperty().bind(txtCode.textProperty().isNotEqualTo(Setting_Language.generate));
    }

    private List<Target> getList() {
        try {
            return targetService.targetList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
        return new ArrayList<>();
    }

    private void deleteData() {
        try {
            if (!tableView.getSelectionModel().isEmpty()) {
                if (AllAlerts.confirmDelete()) {
                    int i = targetService.deleteById(tableView.getSelectionModel().getSelectedItem().getId());
                    if (i >= 1) {
                        AllAlerts.alertDelete();
                        clearData();
                        refreshTable();
                    }
                }
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void saveData() {
        if (!AllAlerts.confirmSave()) {
            return;
        }
        try {
            checkData(searchableComboBox.getSelectionModel().isEmpty(), searchableComboBox);
            checkData(DoubleSetting.parseDoubleOrDefault(txtTarget.getText()) <= 0.0, txtTarget);

            int employee_id = employeeService.getDelegateByName(searchableComboBox.getSelectionModel().getSelectedItem()).getId();
            double target = Double.parseDouble(txtTarget.getText());

            double rate_1 = Double.parseDouble(txtRate1.get());
            double rate_2 = Double.parseDouble(txtRate2.get());
            double rate_3 = Double.parseDouble(txtRate3.get());

            var v = Double.parseDouble(target_ratio1.get());
            var v1 = Double.parseDouble(target_ratio2.get());
            var v2 = Double.parseDouble(target_ratio3.get());

            String note = notes.getText();
            Employees employees = new Employees(employee_id);
            int save;
            if (txtCode.getText().equals(Setting_Language.generate)) {
                if (checkDataIfPresent())
                    throw new DaoException(Error_Text_Show.DUPLICATE_ENTRY);
                save = targetService.insertData(v, rate_1, v1, rate_2, v2, rate_3, target, employees, note);
            } else {
                int code = Integer.parseInt(txtCode.getText());
                save = targetService.updateData(code, v, rate_1, v1, rate_2, v2, rate_3, target, employees, note);
            }
            if (save >= 1) {
                AllAlerts.alertSave();
                clearData();
                refreshTable();

            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void selectData() {
        try {
            Target target = targetService.targetById(tableView.getSelectionModel().getSelectedItem().getId());
            txtCode.setText(String.valueOf(target.getId()));
            txtTarget.setText(String.valueOf(target.getTarget()));
            target_ratio1.set(String.valueOf(target.getTarget_ratio1()));
            target_ratio2.set(String.valueOf(target.getTarget_ratio2()));
            target_ratio3.set(String.valueOf(target.getTarget_ratio3()));
            txtRate1.set(String.valueOf(target.getRate1()));
            txtRate3.set(String.valueOf(target.getRate3()));
            txtRate2.set(String.valueOf(target.getRate2()));
            searchableComboBox.getSelectionModel().select(target.getEmployee_name());
            notes.setText(target.getNotes());

            targetRate1Controller.setAmount();
            targetRate2Controller.setAmount();
            targetRate3Controller.setAmount();

        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void clearData() {
        txtTarget.clear();
        notes.clear();
        txtCode.setText(Setting_Language.generate);
        searchableComboBox.getSelectionModel().clearSelection();
        targetRate1Controller.clearData();
        targetRate2Controller.clearData();
        targetRate3Controller.clearData();
    }

    private void refreshTable() {
        tableView.setItems(FXCollections.observableArrayList(getList()));
        afterAddTarget.setAvailability(searchableComboBox.getSelectionModel().getSelectedItem());
    }

    private boolean checkDataIfPresent() throws DaoException {
        int employee_id = employeeService.getDelegateByName(searchableComboBox.getSelectionModel().getSelectedItem()).getId();
        Optional<Target> target = targetService.targetByDelegateId(employee_id);
        return target.isPresent();
    }

    private void checkData(boolean b, Node node) throws DaoException {
        if (b) {
            node.requestFocus();
            throw new DaoException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }
    }

    @Override
    public Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return textName;
    }
}

@RequiredArgsConstructor
class AddColumnRateController {
    private final TableView<BaseTarget> tableView;

    public void addColumnRate() {
        addColumns("هدف1 ", f -> f.getValue().target_ratio1Property().asString(), f -> f.getValue().rate1Property().asString());
        addColumns("هدف 2", f -> f.getValue().target_ratio2Property().asString(), f -> f.getValue().rate2Property().asString());
        addColumns("هدف 3", f -> f.getValue().target_ratio3Property().asString(), f -> f.getValue().rate3Property().asString());
    }

    private void addColumns(String nameColumn
            , Callback<TableColumn.CellDataFeatures<BaseTarget, String>, ObservableValue<String>> callback1
            , Callback<TableColumn.CellDataFeatures<BaseTarget, String>, ObservableValue<String>> callback2) {
        var columnInterface = new ColumnInterface<BaseTarget, String>() {
            @Override
            public HashMap<String, Callback<TableColumn.CellDataFeatures<BaseTarget, String>, ObservableValue<String>>> STRING_CALLBACK_HASH_MAP() {
                HashMap<String, Callback<TableColumn.CellDataFeatures<BaseTarget, String>, ObservableValue<String>>> hashMap = new HashMap<>();
                hashMap.put("هدف %", callback1);
                hashMap.put("نسبة %", callback2);
                return hashMap;
            }
        };

        tableView.getColumns().add(new AddColumnMix<BaseTarget, String>().getTableColumn(nameColumn, columnInterface));
    }


}

