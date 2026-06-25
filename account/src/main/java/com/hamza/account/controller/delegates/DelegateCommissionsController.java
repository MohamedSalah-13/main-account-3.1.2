package com.hamza.account.controller.delegates;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateCommission;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.service.DelegateCommissionService;
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
@FxmlPath(pathFile = "delegates/delegate-commissions-view.fxml")
public class DelegateCommissionsController {

    private final DelegateCommissionService delegateCommissionService = ServiceRegistry.get(DelegateCommissionService.class);
    private final EmployeeService employeeService = ServiceRegistry.get(EmployeeService.class);

    @FXML
    private StackPane stackPane;

    @FXML
    private TextField txtCommissionId;

    @FXML
    private ComboBox<String> comboDelegate;

    @FXML
    private DatePicker dateCommission;

    @FXML
    private ComboBox<String> comboCommissionType;

    @FXML
    private TextField txtSalesAmount;

    @FXML
    private TextField txtCommissionAmount;

    @FXML
    private TextArea txtNotes;

    @FXML
    private Button btnCalculate;

    @FXML
    private Button btnShow;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnPay;

    @FXML
    private Button btnDelete;

    @FXML
    private Button btnClear;

    @FXML
    private TableView<DelegateCommission> tableCommissions;

    @FXML
    private Label labelTotalCommissions;

    @FXML
    private Label labelPaidCommissions;

    @FXML
    private Label labelRemainingCommissions;

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
        PermissionHelper.disableIfNotAllowed(btnCalculate, PermissionCode.DELEGATES_COMMISSIONS);
        PermissionHelper.disableIfNotAllowed(btnShow, PermissionCode.DELEGATES_COMMISSIONS);
        PermissionHelper.disableIfNotAllowed(btnSave, PermissionCode.DELEGATES_COMMISSIONS);
        PermissionHelper.disableIfNotAllowed(btnPay, PermissionCode.DELEGATES_COMMISSIONS);
        PermissionHelper.disableIfNotAllowed(btnDelete, PermissionCode.DELEGATES_COMMISSIONS);
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableCommissions, DelegateCommission.class);

        tableCommissions.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                fillForm(selected);
            }
        });

        tableCommissions.itemsProperty().addListener((observable, oldValue, newValue) -> updateSummary());
    }

    private void setupInputs() {
        txtCommissionId.setEditable(false);

        setTextFormatter(txtSalesAmount, txtCommissionAmount);

        dateCommission.setValue(LocalDate.now());

        btnDelete.disableProperty().bind(tableCommissions.getSelectionModel().selectedItemProperty().isNull());
        btnPay.disableProperty().bind(tableCommissions.getSelectionModel().selectedItemProperty().isNull());

        btnSave.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> comboDelegate.getSelectionModel().getSelectedItem() == null
                                || comboCommissionType.getSelectionModel().getSelectedItem() == null
                                || dateCommission.getValue() == null
                                || txtCommissionAmount.getText() == null
                                || txtCommissionAmount.getText().isBlank(),
                        comboDelegate.getSelectionModel().selectedItemProperty(),
                        comboCommissionType.getSelectionModel().selectedItemProperty(),
                        dateCommission.valueProperty(),
                        txtCommissionAmount.textProperty()
                )
        );
    }

    private void setupActions() {
        btnShow.setOnAction(event -> refreshData());
        btnCalculate.setOnAction(event -> calculateCommission());
        btnSave.setOnAction(event -> saveCommission());
        btnPay.setOnAction(event -> paySelectedCommission());
        btnDelete.setOnAction(event -> deleteSelected());
        btnClear.setOnAction(event -> clearForm());
    }

    private void loadComboData() {
        comboDelegate.setItems(FXCollections.observableArrayList(getDelegateNames()));
        comboCommissionType.setItems(FXCollections.observableArrayList(delegateCommissionService.getCommissionTypes()));

        if (!comboCommissionType.getItems().isEmpty()) {
            comboCommissionType.getSelectionModel().selectFirst();
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
            if (!PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض عمولات المندوبين");
                return;
            }

            List<DelegateCommission> list = delegateCommissionService.getAllCommissions();
            tableCommissions.setItems(FXCollections.observableArrayList(list));
            updateSummary();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void calculateCommission() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية حساب عمولة المندوب");
                return;
            }

            String delegateName = comboDelegate.getSelectionModel().getSelectedItem();

            if (delegateName == null || delegateName.isBlank()) {
                AllAlerts.alertError("من فضلك اختر المندوب");
                return;
            }

            Employees delegate = employeeService.getDelegateByName(delegateName);

            if (delegate == null || delegate.getId() <= 0) {
                AllAlerts.alertError("لم يتم العثور على بيانات المندوب");
                return;
            }

            LocalDate selectedDate = dateCommission.getValue() == null ? LocalDate.now() : dateCommission.getValue();
            LocalDate dateFrom = selectedDate.withDayOfMonth(1);
            LocalDate dateTo = selectedDate;

            int userId = LogApplication.usersVo == null ? 1 : LogApplication.usersVo.getId();

            DelegateCommission commission = delegateCommissionService.calculateCommission(
                    delegate.getId(),
                    dateFrom,
                    dateTo,
                    userId
            );

            if (commission != null) {
                commission.setDelegateName(delegateName);
                fillForm(commission);
                AllAlerts.alertSaveWithMessage("تم حساب العمولة وحفظها بنجاح");
                refreshData();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private void saveCommission() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية حفظ عمولة المندوب");
                return;
            }

            DelegateCommission commission = buildModelFromForm();

            if (!AllAlerts.confirmSave()) {
                return;
            }

            int result;

            if (commission.getId() > 0) {
                result = delegateCommissionService.update(commission);
            } else {
                result = delegateCommissionService.insert(commission);
            }

            if (result >= 1) {
                AllAlerts.alertSave();
                refreshData();
                clearForm();
            }
        } catch (Exception e) {
            logError(e);
        }
    }

    private DelegateCommission buildModelFromForm() throws Exception {
        DelegateCommission commission = new DelegateCommission();

        String idText = txtCommissionId.getText();
        if (idText != null && !idText.isBlank()) {
            commission.setId(Long.parseLong(idText));
        }

        String delegateName = comboDelegate.getSelectionModel().getSelectedItem();

        if (delegateName == null || delegateName.isBlank()) {
            throw new Exception("من فضلك اختر المندوب");
        }

        Employees delegate = employeeService.getDelegateByName(delegateName);

        if (delegate == null || delegate.getId() <= 0) {
            throw new Exception("لم يتم العثور على بيانات المندوب");
        }

        commission.setDelegateId(delegate.getId());
        commission.setDelegateName(delegateName);

        commission.setCommissionDate(dateCommission.getValue() == null ? LocalDate.now() : dateCommission.getValue());
        commission.setReferenceType("PERIOD");
        commission.setReferenceId(0);

        commission.setSalesAmount(DoubleSetting.parseDoubleOrDefault(txtSalesAmount.getText()));
        commission.setProfitAmount(0);

        String commissionType = comboCommissionType.getSelectionModel().getSelectedItem();
        commission.setCommissionType(commissionType);

        commission.setCommissionRate(0);
        commission.setCommissionAmount(DoubleSetting.parseDoubleOrDefault(txtCommissionAmount.getText()));

        commission.setPaymentStatus("UNPAID");
        commission.setPaidAmount(0);
        commission.setTreasuryId(0);

        commission.setNotes(txtNotes.getText());

        if (LogApplication.usersVo != null) {
            commission.setUserId(LogApplication.usersVo.getId());
        } else {
            commission.setUserId(1);
        }

        return commission;
    }

    private void fillForm(DelegateCommission commission) {
        if (commission == null) {
            return;
        }

        txtCommissionId.setText(commission.getId() <= 0 ? "" : String.valueOf(commission.getId()));

        if (commission.getDelegateName() != null) {
            comboDelegate.getSelectionModel().select(commission.getDelegateName());
        }

        dateCommission.setValue(commission.getCommissionDate() == null ? LocalDate.now() : commission.getCommissionDate());

        if (commission.getCommissionType() != null) {
            comboCommissionType.getSelectionModel().select(commission.getCommissionType());
        }

        txtSalesAmount.setText(String.valueOf(commission.getSalesAmount()));
        txtCommissionAmount.setText(String.valueOf(commission.getCommissionAmount()));
        txtNotes.setText(commission.getNotes() == null ? "" : commission.getNotes());
    }

    private void paySelectedCommission() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية صرف العمولة");
                return;
            }

            DelegateCommission selected = tableCommissions.getSelectionModel().getSelectedItem();

            if (selected == null) {
                AllAlerts.alertError("اختر العمولة أولاً");
                return;
            }

            AllAlerts.alertError("""
                    صرف العمولة يحتاج اختيار خزينة وقيمة الدفع.
                    الشاشة الحالية لا تحتوي على حقل خزينة أو مبلغ دفع.
                    سنضيف شاشة/حوار صرف العمولة في الخطوة التالية.
                    """);

        } catch (Exception e) {
            logError(e);
        }
    }

    private void deleteSelected() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_COMMISSIONS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية حذف العمولة");
                return;
            }

            DelegateCommission selected = tableCommissions.getSelectionModel().getSelectedItem();

            if (selected == null) {
                AllAlerts.alertError("اختر العمولة أولاً");
                return;
            }

            if (!AllAlerts.confirmDelete()) {
                return;
            }

            int result = delegateCommissionService.deleteById(selected.getId());

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
        txtCommissionId.clear();
        comboDelegate.getSelectionModel().clearSelection();

        dateCommission.setValue(LocalDate.now());

        if (!comboCommissionType.getItems().isEmpty()) {
            comboCommissionType.getSelectionModel().selectFirst();
        }

        txtSalesAmount.setText("0.00");
        txtCommissionAmount.setText("0.00");
        txtNotes.clear();
        tableCommissions.getSelectionModel().clearSelection();
    }

    private void updateSummary() {
        List<DelegateCommission> list = tableCommissions.getItems();

        double totalCommissions = delegateCommissionService.sumCommissionAmount(list);
        double paidCommissions = delegateCommissionService.sumPaidAmount(list);
        double remainingCommissions = delegateCommissionService.sumRemainingAmount(list);

        labelTotalCommissions.setText(formatMoney(totalCommissions));
        labelPaidCommissions.setText(formatMoney(paidCommissions));
        labelRemainingCommissions.setText(formatMoney(remainingCommissions));
        labelCount.setText(String.valueOf(list == null ? 0 : list.size()));
    }

    private String formatMoney(double value) {
        return String.format("%,.2f", value);
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}