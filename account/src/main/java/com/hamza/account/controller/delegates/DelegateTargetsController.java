package com.hamza.account.controller.delegates;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateTarget;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.service.DelegateTargetService;
import com.hamza.account.service.EmployeeService;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.others.DoubleSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "delegates/delegate-targets-view.fxml")
public class DelegateTargetsController {

    private final DelegateTargetService delegateTargetService = ServiceRegistry.get(DelegateTargetService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    @FXML
    private StackPane stackPane;

    @FXML
    private TextField txtTargetId;

    @FXML
    private ComboBox<String> comboDelegate;

    @FXML
    private TextField txtTargetName;

    @FXML
    private ComboBox<String> comboTargetType;

    @FXML
    private DatePicker dateFrom;

    @FXML
    private DatePicker dateTo;

    @FXML
    private TextField txtTargetAmount;

    @FXML
    private TextField txtTargetCount;

    @FXML
    private TextArea txtNotes;

    @FXML
    private Button btnShow;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnUpdate;

    @FXML
    private Button btnDelete;

    @FXML
    private Button btnClear;

    @FXML
    private TextField txtSearch;

    @FXML
    private TableView<DelegateTarget> tableTargets;

    @FXML
    private Label labelCount;

    @FXML
    public void initialize() {
        applyPermissions();
        setupTable();
        setupInputs();
        setupActions();
        loadComboData();
        refreshData();
    }

    private void applyPermissions() {
        PermissionHelper.disableIfNotAllowed(btnShow, PermissionCode.TARGETS_SHOW);
        PermissionHelper.disableIfNotAllowed(btnSave, PermissionCode.TARGETS_CREATE);
        PermissionHelper.disableIfNotAllowed(btnUpdate, PermissionCode.TARGETS_UPDATE);
        PermissionHelper.disableIfNotAllowed(btnDelete, PermissionCode.TARGETS_DELETE);
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableTargets, DelegateTarget.class);

        tableTargets.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                fillForm(selected);
            }
        });

        tableTargets.itemsProperty().addListener((observable, oldValue, newValue) -> updateCount());
    }

    private void setupInputs() {
        txtTargetId.setEditable(false);

        setTextFormatter(txtTargetAmount, txtTargetCount);

        dateFrom.setValue(LocalDate.now().withDayOfMonth(1));
        dateTo.setValue(LocalDate.now());

        btnUpdate.disableProperty().bind(tableTargets.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(tableTargets.getSelectionModel().selectedItemProperty().isNull());

        btnSave.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> comboDelegate.getSelectionModel().getSelectedItem() == null
                                || comboTargetType.getSelectionModel().getSelectedItem() == null
                                || txtTargetName.getText() == null
                                || txtTargetName.getText().isBlank()
                                || dateFrom.getValue() == null
                                || dateTo.getValue() == null,
                        comboDelegate.getSelectionModel().selectedItemProperty(),
                        comboTargetType.getSelectionModel().selectedItemProperty(),
                        txtTargetName.textProperty(),
                        dateFrom.valueProperty(),
                        dateTo.valueProperty()
                )
        );
    }

    private void setupActions() {
        btnShow.setOnAction(event -> refreshData());
        btnSave.setOnAction(event -> save());
        btnUpdate.setOnAction(event -> update());
        btnDelete.setOnAction(event -> deleteSelected());
        btnClear.setOnAction(event -> clearForm());

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> search(newValue));

        comboTargetType.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> updateFieldsByTargetType(newValue));
    }

    private void loadComboData() {
        try {
            comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames()));
            comboTargetType.setItems(FXCollections.observableArrayList(delegateTargetService.getTargetTypes()));
        } catch (Exception e) {
            logError(e);
        }
    }

    private List<String> getDelegateNames() {
        try {
            return employeeService.getDelegateNames();
        } catch (DaoException e) {
            logError(e);
            return new ArrayList<>();
        }
    }

    private void refreshData() {
        try {
            if (!PermissionHelper.has(PermissionCode.TARGETS_SHOW)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض الأهداف");
                return;
            }

            List<DelegateTarget> list = delegateTargetService.getAllTargets();
            tableTargets.setItems(FXCollections.observableArrayList(list));
            updateCount();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void search(String text) {
        try {
            List<DelegateTarget> list = delegateTargetService.search(text);
            tableTargets.setItems(FXCollections.observableArrayList(list));
            updateCount();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void save() {
        try {
            if (!PermissionHelper.has(PermissionCode.TARGETS_CREATE)) {
                AllAlerts.alertWarning("ليس لديك صلاحية إضافة هدف");
                return;
            }

            DelegateTarget target = buildModelFromForm(false);

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int result = delegateTargetService.insert(target);

            if (result >= 1) {
                AllAlerts.alertSave();
                refreshData();
                clearForm();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void update() {
        try {
            if (!PermissionHelper.has(PermissionCode.TARGETS_UPDATE)) {
                AllAlerts.alertWarning("ليس لديك صلاحية تعديل هدف");
                return;
            }

            DelegateTarget selected = tableTargets.getSelectionModel().getSelectedItem();

            if (selected == null) {
                AllAlerts.alertError("اختر الهدف أولاً");
                return;
            }

            DelegateTarget target = buildModelFromForm(true);
            target.setId(selected.getId());

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int result = delegateTargetService.update(target);

            if (result >= 1) {
                AllAlerts.alertSave();
                refreshData();
                clearForm();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void deleteSelected() {
        try {
            if (!PermissionHelper.has(PermissionCode.TARGETS_DELETE)) {
                AllAlerts.alertWarning("ليس لديك صلاحية حذف هدف");
                return;
            }

            DelegateTarget selected = tableTargets.getSelectionModel().getSelectedItem();

            if (selected == null) {
                AllAlerts.alertError("اختر الهدف أولاً");
                return;
            }

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int result = delegateTargetService.deleteById(selected.getId());

            if (result >= 1) {
                AllAlerts.alertDelete();
                refreshData();
                clearForm();
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private DelegateTarget buildModelFromForm(boolean update) throws Exception {
        DelegateTarget target = new DelegateTarget();

        String delegateName = comboDelegate.getSelectionModel().getSelectedItem();

        if (delegateName == null || delegateName.isBlank()) {
            throw new Exception("من فضلك اختر المندوب");
        }

        Employees delegate = employeeService.getDelegateByName(delegateName);

        if (delegate == null || delegate.getId() <= 0) {
            throw new Exception("لم يتم العثور على بيانات المندوب");
        }

        target.setDelegateId(delegate.getId());
        target.setDelegateName(delegateName);

        target.setTargetName(txtTargetName.getText());
        target.setTargetType(comboTargetType.getSelectionModel().getSelectedItem());
        target.setPeriodType("MONTHLY");

        target.setPeriodFrom(dateFrom.getValue());
        target.setPeriodTo(dateTo.getValue());

        String targetType = target.getTargetType();

        double amount = DoubleSetting.parseDoubleOrDefault(txtTargetAmount.getText());
        int count = (int) DoubleSetting.parseDoubleOrDefault(txtTargetCount.getText());

        target.setTargetAmount(0);
        target.setTargetQuantity(0);
        target.setTargetCount(0);
        target.setMinProfitPercent(0);

        if ("SALES_AMOUNT".equals(targetType)
                || "NET_SALES_AMOUNT".equals(targetType)
                || "COLLECTION_AMOUNT".equals(targetType)
                || "PROFIT_AMOUNT".equals(targetType)) {
            target.setTargetAmount(amount);
        } else if ("PROFIT_PERCENT".equals(targetType)) {
            target.setMinProfitPercent(amount);
        } else if ("INVOICES_COUNT".equals(targetType)
                || "CUSTOMERS_COUNT".equals(targetType)) {
            target.setTargetCount(count);
        } else if ("ITEM_QUANTITY".equals(targetType)) {
            target.setTargetQuantity(amount);
        }

        target.setStatus("ACTIVE");
        target.setNotes(txtNotes.getText());

        if (LogApplication.usersVo != null) {
            target.setUserId(LogApplication.usersVo.getId());
        } else {
            target.setUserId(1);
        }

        return target;
    }

    private void fillForm(DelegateTarget target) {
        if (target == null) {
            return;
        }

        txtTargetId.setText(String.valueOf(target.getId()));
        comboDelegate.getSelectionModel().select(target.getDelegateName());
        txtTargetName.setText(target.getTargetName());
        comboTargetType.getSelectionModel().select(target.getTargetType());

        dateFrom.setValue(target.getPeriodFrom());
        dateTo.setValue(target.getPeriodTo());

        fillValueFields(target);

        txtNotes.setText(target.getNotes() == null ? "" : target.getNotes());
    }

    private void fillValueFields(DelegateTarget target) {
        txtTargetAmount.setText("0.00");
        txtTargetCount.setText("0");

        String targetType = target.getTargetType();

        if ("SALES_AMOUNT".equals(targetType)
                || "NET_SALES_AMOUNT".equals(targetType)
                || "COLLECTION_AMOUNT".equals(targetType)
                || "PROFIT_AMOUNT".equals(targetType)) {
            txtTargetAmount.setText(String.valueOf(target.getTargetAmount()));
        } else if ("PROFIT_PERCENT".equals(targetType)) {
            txtTargetAmount.setText(String.valueOf(target.getMinProfitPercent()));
        } else if ("ITEM_QUANTITY".equals(targetType)) {
            txtTargetAmount.setText(String.valueOf(target.getTargetQuantity()));
        } else if ("INVOICES_COUNT".equals(targetType)
                || "CUSTOMERS_COUNT".equals(targetType)) {
            txtTargetCount.setText(String.valueOf(target.getTargetCount()));
        }
    }

    private void updateFieldsByTargetType(String targetType) {
        if (targetType == null) {
            txtTargetAmount.setDisable(false);
            txtTargetCount.setDisable(false);
            return;
        }

        boolean countTarget = "INVOICES_COUNT".equals(targetType) || "CUSTOMERS_COUNT".equals(targetType);

        txtTargetCount.setDisable(!countTarget);
        txtTargetAmount.setDisable(countTarget);

        if (countTarget) {
            txtTargetAmount.setText("0.00");
        } else {
            txtTargetCount.setText("0");
        }

        if ("PROFIT_PERCENT".equals(targetType)) {
            txtTargetAmount.setPromptText("نسبة الربح %");
        } else if ("ITEM_QUANTITY".equals(targetType)) {
            txtTargetAmount.setPromptText("كمية الهدف");
        } else {
            txtTargetAmount.setPromptText("قيمة الهدف");
        }
    }

    private void clearForm() {
        txtTargetId.clear();
        comboDelegate.getSelectionModel().clearSelection();
        comboTargetType.getSelectionModel().clearSelection();
        txtTargetName.clear();
        dateFrom.setValue(LocalDate.now().withDayOfMonth(1));
        dateTo.setValue(LocalDate.now());
        txtTargetAmount.setText("0.00");
        txtTargetCount.setText("0");
        txtTargetAmount.setDisable(false);
        txtTargetCount.setDisable(false);
        txtNotes.clear();
        tableTargets.getSelectionModel().clearSelection();
    }

    private void updateCount() {
        int count = tableTargets.getItems() == null ? 0 : tableTargets.getItems().size();
        labelCount.setText(String.valueOf(count));
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}
