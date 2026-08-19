package com.hamza.account.controller.items;

import com.hamza.account.config.UiScale;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.events.UnitsChanged;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.service.UnitsService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

@Log4j2
@FxmlPath(pathFile = "units-view.fxml")
public class UnitsController implements Initializable, AppSettingInterface {

    private static final double MIN_VALUE = 0.0;
    private final String name;
    private final UnitsService unitsService = ServiceRegistry.get(UnitsService.class);
    @FXML
    private TableView<UnitsModel> tableView;
    @FXML
    private Button btnSave, btnRefresh, btnClear;
    @FXML
    private Label labelName, labelCode, labelQuantity, labelTitle, labelSubtitle, labelCount, labelSectionData, labelSectionList;
    @FXML
    private TextField textCode, textName, textCount;
    @FXML
    private StackPane stackPane;
    @FXML
    private FontIcon headerIcon;

    public UnitsController(String name) {
        this.name = name;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tableSetting();
        otherSetting();
        buttonGraphic();
    }

    /**
     * The size the toolbar icons and the header icon render at, scaled from
     * {@link UiScale} so a screen opened while a larger font size is set gets
     * icons to match - the same reasoning that puts every dimension in
     * app-theme.css in em rather than a fixed pixel count.
     */
    private int iconSize() {
        return (int) Math.round(16 * UiScale.factor());
    }

    private FontIcon icon(Ikon code) {
        FontIcon fontIcon = new FontIcon(code);
        fontIcon.setIconSize(iconSize());
        fontIcon.getStyleClass().add("icon-graphic");
        return fontIcon;
    }

    private void buttonGraphic() {
        btnSave.setGraphic(icon(Feather.SAVE));
        btnClear.setGraphic(icon(Feather.ROTATE_CCW));
        btnRefresh.setGraphic(icon(Feather.REFRESH_CW));

        headerIcon.setIconCode(Feather.PACKAGE);
        headerIcon.setIconSize(iconSize() * 2);
    }

    private void tableSetting() {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, UnitsModel.class);
        refreshTable();
    }

    private void otherSetting() {
        var lm = LanguageManager.getInstance();
        setTextFormatter(textCount);
        whenEnterPressed(textName, textCount);
        labelTitle.setText(name);
        labelSubtitle.setText(lm.getString("unit.screen.subtitle"));
        labelSectionData.setText(lm.getString("unit.section.data"));
        labelSectionList.setText(lm.getString("unit.section.list"));
        labelName.setText(lm.getString("name"));
        labelCode.setText(lm.getString("code"));
        // A unit is a name. The number beside it is only the default the item
        // screen offers when you pick it - what a carton holds is per item.
        labelQuantity.setText(lm.getString("unit.default.factor"));
        labelQuantity.setTooltip(new Tooltip(lm.getString("unit.default.factor.hint")));
        textCount.setTooltip(new Tooltip(lm.getString("unit.default.factor.hint")));
        btnSave.setText(lm.getString("common.save"));
        btnRefresh.setText(lm.getString("refresh"));
        btnClear.setText(lm.getString("common.clear"));
        textCode.setText(lm.getString("item.code.generate"));
        textName.setPromptText(lm.getString("column.name_item"));
        textCount.setPromptText(lm.getString("unit.default.factor"));
        // One bind per property: the second call used to replace the first, so the
        // empty-name half of the condition never took effect.
        btnSave.disableProperty().bind(textName.textProperty().isEmpty()
                .or(textCount.textProperty().isEmpty()));
        btnSave.setOnAction(actionEvent -> insertData());
        btnClear.setOnAction(actionEvent -> resetData());
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
                textCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
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
            if (tableView.getSelectionModel().isEmpty())
                throw new UserValidationException(LanguageManager.getInstance().getString("msg.select.file"));

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
                throw new BusinessRuleException(refusal.message());
            }

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int i = unitsService.delete(selectedItem.getUnit_id());
            if (i >= 1) {
                afterData();
            }
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.delete.unit.title"), e);
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
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.save.unit.title"), e);
            e.setFocus();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("item.dialog.save.unit.title"), e);
        }
    }

    /**
     * The unit being edited, or 0 when the form is adding a new one.
     */
    private int editedUnitId() {
        String code = textCode.getText();
        if (code == null || code.equals(LanguageManager.getInstance().getString("item.code.generate"))) {
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
            throw new ValidationException(LanguageManager.getInstance().getString("msg.insert.all"), textName);
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
            throw new ValidationException(LanguageManager.getInstance().getString("msg.insert.all"), textCount);
        }
        if (value <= MIN_VALUE) {
            throw new ValidationException(LanguageManager.getInstance().getString("msg.insert.all"), textCount);
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
            throw new ValidationException(LanguageManager.getInstance().getString("item.error.name.exists"), textName);
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
        textCode.setText(LanguageManager.getInstance().getString("item.code.generate"));
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
        maskerPaneSetting.showMaskerPane(LanguageManager.getInstance().getString("item.dialog.loading.units"), () -> {
            var units = getUnitsModelList();
            Platform.runLater(() -> {
                tableView.setItems(FXCollections.observableArrayList(units));
                labelCount.setText(String.format(LanguageManager.getInstance().getString("report.dashboard.unit.suffix"), units.size()));
            });
        });
    }

    private List<UnitsModel> getUnitsModelList() throws DaoException {
        return unitsService.getUnitsModelList();
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

