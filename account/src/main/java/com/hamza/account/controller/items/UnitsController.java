package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.menu.ActionTable;
import com.hamza.controlsfx.menu.ContextMenuTable;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.language.Setting_Language.CLEAR;
import static com.hamza.controlsfx.language.Setting_Language.generate;
import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "units-view.fxml")
public class UnitsController implements Initializable, AppSettingInterface {

    private static final double MIN_VALUE = 0.0;
    private final DataPublisher dataPublisher;
    private final String name;
    private final UnitsService unitsService = ServiceRegistry.get(UnitsService.class);
    @FXML
    private TableView<UnitsModel> tableView;
    @FXML
    private Button btnSave, btnRefresh, btnClear, btnClose;
    @FXML
    private Label labelName, labelCode, labelQuantity;
    @FXML
    private TextField textCode, textName, textCount;
    @FXML
    private StackPane stackPane;

    public UnitsController(DataPublisher dataPublisher, String name) {
        this.dataPublisher = dataPublisher;
        this.name = name;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tableSetting();
        otherSetting();
        addMenu();
        buttonGraphic();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnSave.setGraphic(createIcon(images.save));
        btnClear.setGraphic(createIcon(images.erase));
        btnClose.setGraphic(createIcon(images.cancel));
        btnRefresh.setGraphic(createIcon(images.refresh));
    }

    private void tableSetting() {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, UnitsModel.class);
        refreshTable();
    }

    private void otherSetting() {
        setTextFormatter(textCount);
        whenEnterPressed(textName, textCount);
        labelName.setText(Setting_Language.WORD_NAME);
        labelCode.setText(Setting_Language.WORD_CODE);
        labelQuantity.setText(Setting_Language.WORD_QUANTITY);
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
        btnClear.setText(CLEAR);
        textCode.setText(generate);
        textName.setPromptText(Setting_Language.NAME_ITEM);
        textCount.setPromptText(Setting_Language.WORD_COUNT);
        btnSave.disableProperty().bind(textName.textProperty().isEmpty());
        btnSave.disableProperty().bind(textCount.textProperty().lessThanOrEqualTo("0.0"));
        btnSave.setOnAction(actionEvent -> insertData());
        btnClear.setOnAction(actionEvent -> resetData());
        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
        btnRefresh.setOnAction(actionEvent -> refreshTable());
        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                deleteData();
            }
        });

        tableView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                selectData();
            }
        });

        tableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                selectData();
            } else {
                resetData();
                textCode.setText(generate);
                textName.clear();
                textCount.clear();
                textName.selectAll();
                textCount.clear();
                textCount.selectAll();
            }
        });
    }

    private void deleteData() {
        try {
            // check selected row
            if (tableView.getSelectionModel().isEmpty()) throw new Exception(Error_Text_Show.PLEASE_SELECT_FILE);

            UnitsModel selectedItem = tableView.getSelectionModel().getSelectedItem();

            // check before delete
            if (selectedItem.getUnit_id() == 1 || selectedItem.getUnit_id() == 2)
                throw new Exception(Error_Text_Show.CANT_DELETE);

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int i = unitsService.delete(selectedItem.getUnit_id());
            if (i >= 1) {
                afterData();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void insertData() {
        try {
            UnitFormData formData = validateAndGetFormData();
            validateUniqueUnitName(formData.name());

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int insertResult = insertOrUpdateUnit(formData);
            if (insertResult >= 1) {
                afterData();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private UnitFormData validateAndGetFormData() throws ValidationException {
        String name = textName.getText();
        if (name.isEmpty()) {
            throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textName);
        }

        double value;
        try {
            value = Double.parseDouble(textCount.getText());
            if (value <= MIN_VALUE) {
                throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textCount);
            }
        } catch (NumberFormatException e) {
            throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textCount);
        }

        return new UnitFormData(name, value);
    }

    private void validateUniqueUnitName(String name) throws Exception {
        boolean nameExists = tableView.getItems().stream()
                .anyMatch(item -> item.getUnit_name().equals(name));
        if (nameExists) {
            throw new Exception("هذا الاسم موجود");
        }
    }

    private int insertOrUpdateUnit(UnitFormData formData) throws Exception {
        boolean isCustomCode = textCode.textProperty().isNotEqualTo(generate).get();
        if (isCustomCode) {
            int code = Integer.parseInt(textCode.getText());
            return unitsService.update(code, formData.name(), formData.value());
        }
        return unitsService.insert(formData.name(), formData.value());
    }

    private void afterData() {
        AllAlerts.alertSave();
        resetData();
        refreshTable();
        dataPublisher.getPublisherAddUnits().notifyObservers();

    }

    private void resetData() {
        textCode.setText(generate);
        textName.clear();
        textCount.clear();
    }

    private void selectData() {
        UnitsModel selectedItem = tableView.getSelectionModel().getSelectedItem();
        textCode.setText(String.valueOf(selectedItem.getUnit_id()));
        textName.setText(selectedItem.getUnit_name());
        textCount.setText(String.valueOf(selectedItem.getValue()));
    }

    private void refreshTable() {
        MaskerPaneSetting maskerPaneSetting = new MaskerPaneSetting(stackPane);
        maskerPaneSetting.showMaskerPane(() -> {
            tableView.setItems(FXCollections.observableArrayList(getUnitsModelList()));
        });
    }

    private List<UnitsModel> getUnitsModelList() {
        try {
            return unitsService.getUnitsModelList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            return List.of();
        }
    }

    private void addMenu() {
        ContextMenuTable contextMenu = new ContextMenuTable(new ActionTable() {
            @Override
            public void actionAdd() {
                resetData();
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
        tableView.setContextMenu(contextMenu);
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return name;
    }

    @Override
    public boolean resize() {
        return true;
    }
}

record UnitFormData(String name, double value) {
}

class ValidationException extends Exception {
    private final TextField focusField;

    public ValidationException(String message, TextField focusField) {
        super(message);
        this.focusField = focusField;
    }

    public void setFocus() {
        focusField.requestFocus();
    }
}

