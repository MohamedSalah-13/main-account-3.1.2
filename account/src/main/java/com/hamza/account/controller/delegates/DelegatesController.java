package com.hamza.account.controller.delegates;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateProfile;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.service.DelegateService;
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

import java.util.ArrayList;
import java.util.List;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "delegates/delegates-view.fxml")
public class DelegatesController {

    private final DelegateService delegateService = ServiceRegistry.get(DelegateService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    @FXML
    private StackPane stackPane;

    @FXML
    private TextField txtDelegateId;

    @FXML
    private ComboBox<String> comboDelegate;

    @FXML
    private ComboBox<String> comboArea;

    @FXML
    private ComboBox<String> comboSupervisor;

    @FXML
    private ComboBox<String> comboCommissionType;

    @FXML
    private TextField txtCommissionValue;

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
    private TableView<DelegateProfile> tableDelegates;

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
        PermissionHelper.disableIfNotAllowed(btnSave, PermissionCode.DELEGATES_UPDATE_PROFILE);
        PermissionHelper.disableIfNotAllowed(btnUpdate, PermissionCode.DELEGATES_UPDATE_PROFILE);
        PermissionHelper.disableIfNotAllowed(btnDelete, PermissionCode.DELEGATES_UPDATE_PROFILE);
        PermissionHelper.disableIfNotAllowed(btnShow, PermissionCode.DELEGATES_SHOW);
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableDelegates, DelegateProfile.class);

        tableDelegates.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                fillForm(selected);
            }
        });

        tableDelegates.itemsProperty().addListener((observable, oldValue, newValue) -> updateCount());
    }

    private void setupInputs() {
        setTextFormatter(txtCommissionValue);

        txtDelegateId.setEditable(false);

        comboArea.setDisable(true);

        btnUpdate.disableProperty().bind(tableDelegates.getSelectionModel().selectedItemProperty().isNull());
        btnDelete.disableProperty().bind(tableDelegates.getSelectionModel().selectedItemProperty().isNull());

        btnSave.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> comboDelegate.getSelectionModel().getSelectedItem() == null,
                        comboDelegate.getSelectionModel().selectedItemProperty()
                )
        );
    }

    private void setupActions() {
        btnShow.setOnAction(event -> refreshData());
        btnSave.setOnAction(event -> saveOrUpdate());
        btnUpdate.setOnAction(event -> saveOrUpdate());
        btnDelete.setOnAction(event -> deleteSelected());
        btnClear.setOnAction(event -> clearForm());

        comboDelegate.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                loadDelegateByName(newValue);
            }
        });

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> search(newValue));
    }

    private void loadComboData() {
        try {
            comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames()));
            comboSupervisor.setItems(FXCollections.observableArrayList(getDelegateNames()));
            comboCommissionType.setItems(FXCollections.observableArrayList(delegateService.getCommissionTypes()));
            comboCommissionType.getSelectionModel().select("NONE");
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
            if (!PermissionHelper.has(PermissionCode.DELEGATES_SHOW)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض المندوبين");
                return;
            }

            List<DelegateProfile> list = delegateService.getAllDelegates();
            tableDelegates.setItems(FXCollections.observableArrayList(list));
            updateCount();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void search(String text) {
        try {
            List<DelegateProfile> list = delegateService.search(text);
            tableDelegates.setItems(FXCollections.observableArrayList(list));
            updateCount();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void loadDelegateByName(String delegateName) {
        try {
            DelegateProfile delegate = delegateService.getDelegateByName(delegateName);

            if (delegate != null) {
                fillForm(delegate);
                return;
            }

            Employees employee = employeeService.getDelegateByName(delegateName);

            if (employee != null) {
                txtDelegateId.setText(String.valueOf(employee.getId()));
                txtCommissionValue.setText("0.00");
                txtNotes.clear();
                comboCommissionType.getSelectionModel().select("NONE");
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void fillForm(DelegateProfile delegate) {
        if (delegate == null) {
            return;
        }

        txtDelegateId.setText(String.valueOf(delegate.getDelegateId()));

        if (delegate.getDelegateName() != null) {
            comboDelegate.getSelectionModel().select(delegate.getDelegateName());
        }

        if (delegate.getAreaName() != null) {
            comboArea.getSelectionModel().select(delegate.getAreaName());
        }

        if (delegate.getSupervisorName() != null) {
            comboSupervisor.getSelectionModel().select(delegate.getSupervisorName());
        }

        if (delegate.getCommissionType() != null) {
            comboCommissionType.getSelectionModel().select(delegate.getCommissionType());
        } else {
            comboCommissionType.getSelectionModel().select("NONE");
        }

        txtCommissionValue.setText(String.valueOf(delegate.getCommissionValue()));
        txtNotes.setText(delegate.getNotes() == null ? "" : delegate.getNotes());
    }

    private void saveOrUpdate() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_UPDATE_PROFILE)) {
                AllAlerts.alertWarning("ليس لديك صلاحية تعديل بيانات المندوب");
                return;
            }

            DelegateProfile profile = buildModelFromForm();

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int result = delegateService.saveOrUpdate(profile);

            if (result >= 1) {
                AllAlerts.alertSave();
                refreshData();
                clearForm();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private DelegateProfile buildModelFromForm() throws Exception {
        DelegateProfile profile = new DelegateProfile();

        String delegateName = comboDelegate.getSelectionModel().getSelectedItem();

        if (delegateName == null || delegateName.isBlank()) {
            throw new Exception("من فضلك اختر المندوب");
        }

        Employees delegate = employeeService.getDelegateByName(delegateName);

        if (delegate == null || delegate.getId() <= 0) {
            throw new Exception("لم يتم العثور على بيانات المندوب");
        }

        profile.setDelegateId(delegate.getId());
        profile.setDelegateName(delegateName);

        String supervisorName = comboSupervisor.getSelectionModel().getSelectedItem();
        if (supervisorName != null && !supervisorName.isBlank()) {
            Employees supervisor = employeeService.getDelegateByName(supervisorName);
            if (supervisor != null) {
                profile.setSupervisorId(supervisor.getId());
                profile.setSupervisorName(supervisorName);
            }
        }

        String commissionType = comboCommissionType.getSelectionModel().getSelectedItem();
        profile.setCommissionType(commissionType == null ? "NONE" : commissionType);

        profile.setCommissionValue(DoubleSetting.parseDoubleOrDefault(txtCommissionValue.getText()));
        profile.setNotes(txtNotes.getText());
        profile.setActive(true);

        if (LogApplication.usersVo != null) {
            profile.setUserId(LogApplication.usersVo.getId());
        } else {
            profile.setUserId(1);
        }

        return profile;
    }

    private void deleteSelected() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_UPDATE_PROFILE)) {
                AllAlerts.alertWarning("ليس لديك صلاحية حذف بيانات المندوب");
                return;
            }

            DelegateProfile selected = tableDelegates.getSelectionModel().getSelectedItem();

            if (selected == null) {
                AllAlerts.alertError("اختر مندوبًا أولاً");
                return;
            }

            if (selected.getProfileId() <= 0) {
                AllAlerts.alertError("لا توجد بيانات إضافية محفوظة لهذا المندوب");
                return;
            }

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int result = delegateService.deleteByProfileId(selected.getProfileId());

            if (result >= 1) {
                AllAlerts.alertDelete();
                refreshData();
                clearForm();
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void clearForm() {
        txtDelegateId.clear();
        comboDelegate.getSelectionModel().clearSelection();
        comboArea.getSelectionModel().clearSelection();
        comboSupervisor.getSelectionModel().clearSelection();
        comboCommissionType.getSelectionModel().select("NONE");
        txtCommissionValue.setText("0.00");
        txtNotes.clear();
        tableDelegates.getSelectionModel().clearSelection();
    }

    private void updateCount() {
        int count = tableDelegates.getItems() == null ? 0 : tableDelegates.getItems().size();
        labelCount.setText(String.valueOf(count));
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}
