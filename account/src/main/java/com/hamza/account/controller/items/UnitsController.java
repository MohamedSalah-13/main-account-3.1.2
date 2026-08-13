package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.UnitsChanged;
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
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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

    public UnitsController(String name) {
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
        // A unit is a name. The number beside it is only the default the item
        // screen offers when you pick it - what a carton holds is per item.
        labelQuantity.setText(Setting_Language.UNIT_DEFAULT_FACTOR);
        labelQuantity.setTooltip(new Tooltip(Setting_Language.UNIT_DEFAULT_FACTOR_HINT));
        textCount.setTooltip(new Tooltip(Setting_Language.UNIT_DEFAULT_FACTOR_HINT));
        btnSave.setText(Setting_Language.WORD_SAVE);
        btnClose.setText(Setting_Language.WORD_CLOSE);
        btnRefresh.setText(Setting_Language.WORD_REFRESH);
        btnClear.setText(CLEAR);
        textCode.setText(generate);
        textName.setPromptText(Setting_Language.NAME_ITEM);
        textCount.setPromptText(Setting_Language.UNIT_DEFAULT_FACTOR);
        // One bind per property: the second call used to replace the first, so the
        // empty-name half of the condition never took effect.
        btnSave.disableProperty().bind(textName.textProperty().isEmpty()
                .or(textCount.textProperty().isEmpty()));
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

            // A unit used to be undeletable for being one of the two the database
            // ships with, which said nothing about whether anyone relies on it: a
            // business that sells nothing by the carton was stuck with "كرتونه",
            // while a unit it added and used everywhere could be deleted. What
            // matters is whether anything points at it - and the rule answers with
            // what and how many, so the refusal names the invoices rather than
            // leaving the user to go looking for them.
            var refusal = unitsService.checkDelete(selectedItem.getUnit_id());
            if (refusal != null) {
                throw new Exception(refusal.message());
            }

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int i = unitsService.delete(selectedItem.getUnit_id());
            if (i >= 1) {
                afterData();
            }
        } catch (Exception e) {
            AllAlerts.handleError("حذف الوحدة", e);
        }
    }

    private void insertData() {
        try {
            int editedUnitId = editedUnitId();
            UnitFormData formData = validateAndGetFormData();
            validateUniqueUnitName(formData.name(), editedUnitId);

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int insertResult = editedUnitId == 0
                    ? unitsService.insert(formData.name(), formData.value())
                    : unitsService.update(editedUnitId, formData.name(), formData.value());
            if (insertResult >= 1) {
                afterData();
            }
        } catch (ValidationException e) {
            AllAlerts.handleError("حفظ الوحدة", e);
            e.setFocus();
        } catch (Exception e) {
            AllAlerts.handleError("حفظ الوحدة", e);
        }
    }

    /**
     * The unit being edited, or 0 when the form is adding a new one.
     */
    private int editedUnitId() {
        String code = textCode.getText();
        if (code == null || code.equals(generate)) {
            return 0;
        }
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private UnitFormData validateAndGetFormData() throws ValidationException {
        String name = textName.getText();
        if (name == null || name.isBlank()) {
            throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textName);
        }

        // Left blank, a unit converts one to one - which is what a unit that is
        // only a name should do. Anything typed still has to be a real number.
        String text = textCount.getText();
        if (text == null || text.isBlank()) {
            return new UnitFormData(name.trim(), 1);
        }

        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textCount);
        }
        if (value <= MIN_VALUE) {
            throw new ValidationException(Error_Text_Show.PLEASE_INSERT_ALL_DATA, textCount);
        }

        return new UnitFormData(name.trim(), value);
    }

    /**
     * The name has to be free, but a unit does not clash with itself - renaming
     * "كرتونه" to "كرتونة" used to be rejected as a duplicate of the row being
     * edited.
     */
    private void validateUniqueUnitName(String name, int editedUnitId) throws ValidationException {
        boolean nameExists = tableView.getItems().stream()
                .filter(item -> item.getUnit_id() != editedUnitId)
                .anyMatch(item -> item.getUnit_name().equals(name));
        if (nameExists) {
            throw new ValidationException("هذا الاسم موجود", textName);
        }
    }

    private void afterData() {
        AllAlerts.alertSave();
        resetData();
        refreshTable();
        var eventBus = ServiceRegistry.get(EventBus.class);
        if (eventBus != null) eventBus.publish(new UnitsChanged());

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
        maskerPaneSetting.showMaskerPane("تحميل الوحدات", () -> {
            var units = getUnitsModelList();
            Platform.runLater(() -> tableView.setItems(FXCollections.observableArrayList(units)));
        });
    }

    private List<UnitsModel> getUnitsModelList() throws DaoException {
        return unitsService.getUnitsModelList();
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

class ValidationException extends com.hamza.controlsfx.error.UserValidationException {
    private final TextField focusField;

    public ValidationException(String message, TextField focusField) {
        super(message);
        this.focusField = focusField;
    }

    public void setFocus() {
        focusField.requestFocus();
    }
}

