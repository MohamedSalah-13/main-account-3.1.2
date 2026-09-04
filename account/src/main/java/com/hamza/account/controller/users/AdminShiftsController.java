package com.hamza.account.controller.users;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.NamesTables;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.shift.ShiftMode;
import com.hamza.account.features.shift.ShiftPolicy;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.account.features.shift.ShiftTrackingMode;
import com.hamza.account.features.shift.TreasuryShiftPolicy;
import com.hamza.account.features.shift.ShiftCashAuditService;
import com.hamza.account.features.shift.ShiftCashLedgerEntry;
import com.hamza.account.features.shift.ShiftCashLedgerFilter;
import com.hamza.account.features.shift.ShiftCashSource;
import com.hamza.account.features.shift.ShiftCloseRequest;
import com.hamza.account.features.shift.ShiftLedgerAction;
import com.hamza.account.features.shift.ShiftReconciliationResult;
import com.hamza.account.features.shift.ShiftReconciliationService;
import com.hamza.account.features.shift.CashierTreasuryAssignment;
import com.hamza.account.features.shift.CashierTreasuryAssignmentEvent;
import com.hamza.account.features.shift.CashierTreasuryAssignmentService;
import com.hamza.account.features.shift.ShiftCashHandover;
import com.hamza.account.features.shift.ShiftCashHandoverPolicy;
import com.hamza.account.features.shift.ShiftCashHandoverService;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.Users;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.service.TreasuryService;
import com.hamza.account.service.UsersService;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ProgressIndicator;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

@FxmlPath(pathFile = "admin-shifts-view.fxml")
@Log4j2
public class AdminShiftsController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserShiftService shifts = ServiceRegistry.get(UserShiftService.class);
    private final ShiftPolicyService policies = ServiceRegistry.get(ShiftPolicyService.class);
    private final ShiftCashAuditService audit = ServiceRegistry.get(ShiftCashAuditService.class);
    private final ShiftReconciliationService reconciliation = ServiceRegistry.get(ShiftReconciliationService.class);
    private final ShiftReportService shiftReports = ServiceRegistry.get(ShiftReportService.class);
    private final CashierTreasuryAssignmentService assignments =
            ServiceRegistry.get(CashierTreasuryAssignmentService.class);
    private final ShiftCashHandoverService handovers = ServiceRegistry.get(ShiftCashHandoverService.class);
    private final UsersService users = ServiceRegistry.get(UsersService.class);
    private final TreasuryService treasuries = ServiceRegistry.get(TreasuryService.class);
    private final Print_Reports printReports = new Print_Reports();

    @FXML private TableView<UserShift> tableView;
    @FXML private Button btnRefresh, btnForceClose, btnSavePolicy;
    @FXML private ComboBox<ShiftMode> comboShiftMode;
    @FXML private CheckBox checkBlindClose, checkAutoPrintZ, checkVarianceReason,
            checkSupervisorApproval, checkEnforceTreasuryAssignments, checkAssignmentDefault;
    @FXML private TextField txtVarianceTolerance;
    @FXML private VBox treasuryPolicyRows;
    @FXML private TitledPane ledgerPane;
    @FXML private TableView<ShiftCashLedgerEntry> ledgerTable;
    @FXML private ComboBox<ShiftLedgerAction> comboLedgerAction;
    @FXML private ComboBox<ShiftCashSource> comboLedgerSource;
    @FXML private TextField txtLedgerSourceId;
    @FXML private Button btnLedgerRefresh, btnLedgerClear, btnLedgerReconcile;
    @FXML private Label labelLedgerState, labelLedgerReconciliation;
    @FXML private ProgressIndicator ledgerProgress;
    @FXML private TitledPane approvalPane;
    @FXML private TableView<ShiftCloseRequest> approvalTable;
    @FXML private Button btnApprovalRefresh, btnApproveClose, btnRejectClose;
    @FXML private ProgressIndicator mainProgress, approvalProgress;
    @FXML private TitledPane assignmentPane;
    @FXML private ComboBox<Users> comboAssignmentUser;
    @FXML private ComboBox<Treasury> comboAssignmentTreasury;
    @FXML private TableView<CashierTreasuryAssignment> assignmentTable;
    @FXML private TableView<CashierTreasuryAssignmentEvent> assignmentHistoryTable;
    @FXML private Button btnAssignTreasury, btnDeactivateAssignment, btnAssignmentRefresh;
    @FXML private ProgressIndicator assignmentProgress;
    @FXML private TitledPane handoverPane;
    @FXML private ComboBox<Treasury> comboHandoverSource, comboHandoverTarget;
    @FXML private TextField txtHandoverFloat;
    @FXML private CheckBox checkHandoverEnabled;
    @FXML private TableView<ShiftCashHandoverPolicy> handoverPolicyTable;
    @FXML private TableView<ShiftCashHandover> handoverTable;
    @FXML private Button btnSaveHandoverPolicy, btnReceiveHandover,
            btnApproveHandoverOpen, btnHandoverRefresh;
    @FXML private ProgressIndicator handoverProgress;
    private long ledgerRequest;
    private long reconciliationRequest;
    private long dataRequest;
    private long approvalRequest;
    private long handoverRequest;
    private boolean mayDecideClose;
    private boolean mayManageHandovers;
    private boolean mayReceiveHandovers;

    @FXML
    public void initialize() {
        setupTable();
        setupActions();
        setupPolicyEditor();
        setupAssignments();
        setupHandovers();
        setupApprovals();
        setupLedger();
        refreshData();
    }

    private void setupTable() {
        tableView.getColumns().addAll(
                Columns.number(NamesTables.CODE, UserShift::getId),
                Columns.text("user.shift.column.username", UserShift::getUsername),
                Columns.text("invoice.treasury", UserShift::getTreasuryName),
                Columns.text("user.shift.column.open.time", row -> formatTime(row.getOpenTime())),
                Columns.text("user.shift.column.close.time", row -> formatTime(row.getCloseTime())),
                Columns.number("user.shift.column.open.balance", UserShift::getOpenBalance),
                Columns.number("user.shift.column.close.balance", UserShift::getCloseBalance),
                Columns.text("case", row -> message("user.shift.status." + row.getStatus().name().toLowerCase())),
                Columns.number("user.shift.label.total.sales", UserShift::getTotalSales),
                Columns.number("user.shift.label.sales.returns", UserShift::getTotalSalesReturns),
                Columns.number("user.shift.label.expenses", UserShift::getTotalExpenses),
                Columns.number("user.shift.label.expected.balance", UserShift::getExpectedBalance),
                Columns.number("user.shift.label.difference", UserShift::getDifference));
    }

    private void setupActions() {
        btnRefresh.setOnAction(event -> refreshData());
        btnForceClose.setOnAction(event -> forceCloseSelected());
        btnSavePolicy.setOnAction(event -> savePolicy());
        btnLedgerRefresh.setOnAction(event -> refreshLedger());
        btnLedgerClear.setOnAction(event -> clearLedgerFilters());
        btnLedgerReconcile.setOnAction(event -> reconcileSelected());
    }

    private void setupApprovals() {
        approvalTable.getColumns().addAll(
                Columns.number(NamesTables.CODE, ShiftCloseRequest::id),
                Columns.number("user.shift.approval.column.shift", ShiftCloseRequest::shiftId),
                Columns.text("user.shift.column.username", ShiftCloseRequest::shiftUsername),
                Columns.text("invoice.treasury", ShiftCloseRequest::treasuryName),
                Columns.text("user.shift.approval.column.requester", ShiftCloseRequest::requestedByUsername),
                Columns.text("user.shift.approval.column.time", row -> formatTime(row.requestedAt())),
                Columns.number("user.shift.approval.column.actual", ShiftCloseRequest::actualBalance),
                Columns.number("user.shift.label.expected.balance", ShiftCloseRequest::expectedBalance),
                Columns.number("user.shift.label.difference", ShiftCloseRequest::difference),
                Columns.text("user.shift.approval.column.reason", ShiftCloseRequest::reason));
        mayDecideClose = AuthorizationGuard.isGranted(AppPermissions.SHIFT_FORCE_CLOSE);
        approvalPane.setDisable(!mayDecideClose);
        setApprovalBusy(false);
        btnApprovalRefresh.setOnAction(event -> refreshApprovals());
        btnApproveClose.setOnAction(event -> approveSelected());
        btnRejectClose.setOnAction(event -> rejectSelected());
    }

    private void setupLedger() {
        ledgerTable.getColumns().addAll(
                Columns.number(NamesTables.CODE, ShiftCashLedgerEntry::id),
                Columns.number("user.shift.ledger.column.origin.shift", ShiftCashLedgerEntry::originShiftId),
                Columns.text("user.shift.ledger.column.time", row -> formatTime(row.occurredAt())),
                Columns.text("user.shift.ledger.column.action", row ->
                        message("user.shift.ledger.action." + row.action().name().toLowerCase())),
                Columns.text("user.shift.ledger.column.source", row -> sourceText(row.source())),
                Columns.number("user.shift.ledger.column.source.id", ShiftCashLedgerEntry::sourceId),
                Columns.text("invoice.treasury", ShiftCashLedgerEntry::treasuryName),
                Columns.text("user.shift.ledger.column.actor", ShiftCashLedgerEntry::actorUsername),
                Columns.number("user.shift.ledger.column.income.delta", ShiftCashLedgerEntry::incomeDelta),
                Columns.number("user.shift.ledger.column.output.delta", ShiftCashLedgerEntry::outputDelta),
                Columns.number("user.shift.ledger.column.net.delta", ShiftCashLedgerEntry::netDelta),
                Columns.text("user.shift.ledger.column.reason", row ->
                        row.reason() == null || row.reason().isBlank() ? "-" : row.reason()));
        comboLedgerAction.setItems(FXCollections.observableArrayList(ShiftLedgerAction.values()));
        comboLedgerAction.setConverter(enumConverter("user.shift.ledger.action."));
        comboLedgerSource.setItems(FXCollections.observableArrayList(ShiftCashSource.values()));
        comboLedgerSource.setConverter(new StringConverter<>() {
            @Override public String toString(ShiftCashSource value) {
                return value == null ? "" : sourceText(value);
            }
            @Override public ShiftCashSource fromString(String text) { throw new UnsupportedOperationException(); }
        });
        txtLedgerSourceId.setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));
        whenEnterPressed(txtLedgerSourceId, btnLedgerRefresh);
        boolean mayView = AuthorizationGuard.isGranted(AppPermissions.SHIFT_LEDGER_VIEW);
        ledgerPane.setDisable(!mayView);
        btnLedgerReconcile.setDisable(!mayView);
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (mayView) {
                refreshLedger();
                labelLedgerReconciliation.setText(message("user.shift.reconciliation.not.run"));
            }
        });
        showLedgerState("user.shift.ledger.select.shift");
        labelLedgerReconciliation.setText(message("user.shift.reconciliation.not.run"));
    }

    private void reconcileSelected() {
        UserShift selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            labelLedgerReconciliation.setText(message("user.shift.ledger.select.shift"));
            return;
        }
        long request = ++reconciliationRequest;
        ledgerProgress.setVisible(true);
        labelLedgerReconciliation.setText(message("user.shift.reconciliation.running"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return reconciliation.reconcile(selected.getId());
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            if (request != reconciliationRequest) return;
            ledgerProgress.setVisible(false);
            if (error != null) {
                labelLedgerReconciliation.setText(message("user.shift.reconciliation.error"));
                AllAlerts.handleError(message("user.shift.reconciliation.error"), rootCause(error));
                return;
            }
            showReconciliation(result);
        }));
    }

    private void showReconciliation(ShiftReconciliationResult result) {
        String status = message("user.shift.reconciliation.status."
                + result.status().name().toLowerCase());
        String snapshot = !result.snapshotPresent()
                ? message("user.shift.reconciliation.snapshot.missing")
                : result.ledgerComplete()
                ? message("user.shift.reconciliation.snapshot.complete")
                : message("user.shift.reconciliation.snapshot.legacy");
        labelLedgerReconciliation.setText(message("user.shift.reconciliation.summary",
                status, snapshot, result.ledgerIncome(), result.ledgerOutput(),
                result.sourceMismatchCount(), result.duplicateCreateCount(),
                result.invalidReasonCount(), result.postCloseEntryCount()));
    }

    private void clearLedgerFilters() {
        comboLedgerAction.setValue(null);
        comboLedgerSource.setValue(null);
        txtLedgerSourceId.clear();
        refreshLedger();
    }

    private void refreshLedger() {
        UserShift selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ledgerTable.getItems().clear();
            showLedgerState("user.shift.ledger.select.shift");
            return;
        }
        Integer sourceId = txtLedgerSourceId.getText() == null || txtLedgerSourceId.getText().isBlank()
                ? null : Integer.valueOf(txtLedgerSourceId.getText());
        ShiftCashLedgerFilter filter = new ShiftCashLedgerFilter(selected.getId(),
                comboLedgerAction.getValue(), comboLedgerSource.getValue(), sourceId,
                ShiftCashLedgerFilter.DEFAULT_LIMIT);
        long request = ++ledgerRequest;
        ledgerProgress.setVisible(true);
        labelLedgerState.setText(message("user.shift.ledger.loading"));
        CompletableFuture.supplyAsync(() -> {
            try {
                return audit.search(filter);
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((rows, error) -> Platform.runLater(() -> {
            if (request != ledgerRequest) return;
            ledgerProgress.setVisible(false);
            if (error != null) {
                showLedgerState("user.shift.ledger.error.load");
                AllAlerts.handleError(message("user.shift.ledger.error.load"), rootCause(error));
                return;
            }
            ledgerTable.setItems(FXCollections.observableArrayList(rows));
            labelLedgerState.setText(message("user.shift.ledger.rows", rows.size()));
        }));
    }

    private void showLedgerState(String key) {
        labelLedgerState.setText(message(key));
        ledgerProgress.setVisible(false);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String sourceText(ShiftCashSource source) {
        return message("user.shift.ledger.source." + source.name().toLowerCase());
    }

    private void setupPolicyEditor() {
        comboShiftMode.setItems(FXCollections.observableArrayList(ShiftMode.values()));
        comboShiftMode.setConverter(enumConverter("user.shift.mode."));
        setTextFormatter(txtVarianceTolerance);
        boolean mayManage = AuthorizationGuard.isGranted(AppPermissions.SHIFT_POLICY_MANAGE);
        btnSavePolicy.setDisable(!mayManage);
        treasuryPolicyRows.setDisable(!mayManage);
        comboShiftMode.setDisable(!mayManage);
        txtVarianceTolerance.setDisable(!mayManage);
        checkBlindClose.setDisable(!mayManage);
        checkAutoPrintZ.setDisable(!mayManage);
        checkVarianceReason.setDisable(!mayManage);
        checkSupervisorApproval.setDisable(!mayManage);
        checkEnforceTreasuryAssignments.setDisable(!mayManage);
        btnForceClose.setDisable(!AuthorizationGuard.isGranted(AppPermissions.SHIFT_FORCE_CLOSE));
        try {
            ShiftPolicy policy = policies.current();
            comboShiftMode.setValue(policy.mode());
            checkBlindClose.setSelected(policy.blindClose());
            checkAutoPrintZ.setSelected(policy.autoPrintZ());
            checkVarianceReason.setSelected(policy.requireVarianceReason());
            checkSupervisorApproval.setSelected(policy.requireSupervisorApproval());
            checkEnforceTreasuryAssignments.setSelected(policy.enforceTreasuryAssignments());
            txtVarianceTolerance.setText(policy.varianceTolerance().toPlainString());
            buildTreasuryRows(policies.treasuries());
        } catch (DaoException e) {
            AllAlerts.handleError(message("user.shift.policy.error.load"), e);
        }
    }

    private void buildTreasuryRows(List<TreasuryShiftPolicy> items) {
        treasuryPolicyRows.getChildren().clear();
        for (TreasuryShiftPolicy item : items) {
            Label name = new Label(item.treasuryName());
            name.setMinWidth(180);
            ComboBox<ShiftTrackingMode> mode = new ComboBox<>(FXCollections.observableArrayList(ShiftTrackingMode.values()));
            mode.setConverter(enumConverter("user.shift.tracking."));
            mode.setValue(item.trackingMode());
            mode.setUserData(item.treasuryId());
            treasuryPolicyRows.getChildren().add(new HBox(12, name, mode));
        }
    }

    private void savePolicy() {
        try {
            ShiftPolicy policy = new ShiftPolicy(comboShiftMode.getValue(), checkBlindClose.isSelected(),
                    checkAutoPrintZ.isSelected(), parseMoney(txtVarianceTolerance.getText()),
                    checkVarianceReason.isSelected(), checkSupervisorApproval.isSelected(),
                    checkEnforceTreasuryAssignments.isSelected());
            List<TreasuryShiftPolicy> treasuryPolicies = new ArrayList<>();
            for (var row : treasuryPolicyRows.getChildren()) {
                HBox box = (HBox) row;
                Label name = (Label) box.getChildren().get(0);
                @SuppressWarnings("unchecked") ComboBox<ShiftTrackingMode> mode =
                        (ComboBox<ShiftTrackingMode>) box.getChildren().get(1);
                treasuryPolicies.add(new TreasuryShiftPolicy(
                        (Integer) mode.getUserData(), name.getText(), mode.getValue()));
            }
            policies.saveConfiguration(policy, treasuryPolicies);
            AllAlerts.alertSaveWithMessage(message("user.shift.policy.saved"));
        } catch (Exception e) {
            AllAlerts.handleError(message("user.shift.policy.error.save"), e);
        }
    }

    private void setupAssignments() {
        assignmentTable.getColumns().addAll(
                Columns.text("user.shift.assignment.column.user", CashierTreasuryAssignment::username),
                Columns.text("user.shift.assignment.column.treasury", CashierTreasuryAssignment::treasuryName),
                Columns.text("user.shift.assignment.column.default", row -> message(
                        row.defaultTreasury() ? "yes" : "no")),
                Columns.text("user.shift.assignment.column.active", row -> message(
                        row.active() ? "user.shift.assignment.status.active"
                                : "user.shift.assignment.status.inactive")),
                Columns.text("user.shift.assignment.column.updated.by",
                        CashierTreasuryAssignment::updatedByUsername),
                Columns.text("user.shift.assignment.column.updated.at",
                        row -> formatTime(row.updatedAt())));
        assignmentHistoryTable.getColumns().addAll(
                Columns.text("user.shift.assignment.history.column.time",
                        row -> formatTime(row.occurredAt())),
                Columns.text("user.shift.assignment.column.user",
                        CashierTreasuryAssignmentEvent::username),
                Columns.text("user.shift.assignment.column.treasury",
                        CashierTreasuryAssignmentEvent::treasuryName),
                Columns.text("user.shift.assignment.history.column.action",
                        row -> message("user.shift.assignment.history.action."
                                + row.action().name().toLowerCase())),
                Columns.text("user.shift.assignment.history.column.state",
                        this::assignmentState),
                Columns.text("user.shift.assignment.history.column.actor",
                        CashierTreasuryAssignmentEvent::actorUsername));
        comboAssignmentUser.setConverter(new StringConverter<>() {
            @Override public String toString(Users value) {
                return value == null ? "" : value.getUsername();
            }
            @Override public Users fromString(String text) { throw new UnsupportedOperationException(); }
        });
        comboAssignmentTreasury.setConverter(new StringConverter<>() {
            @Override public String toString(Treasury value) {
                return value == null ? "" : value.getName();
            }
            @Override public Treasury fromString(String text) { throw new UnsupportedOperationException(); }
        });
        boolean mayManage = AuthorizationGuard.isGranted(AppPermissions.SHIFT_POLICY_MANAGE);
        assignmentPane.setDisable(!mayManage);
        btnAssignTreasury.setOnAction(event -> assignTreasury());
        btnDeactivateAssignment.setOnAction(event -> deactivateAssignment());
        btnAssignmentRefresh.setOnAction(event -> refreshAssignments());
        assignmentTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) ->
                btnDeactivateAssignment.setDisable(selected == null || !selected.active()));
        setAssignmentBusy(false);
        if (mayManage) refreshAssignments();
    }

    private void refreshAssignments() {
        setAssignmentBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                var trackedIds = policies.treasuries().stream()
                        .filter(item -> item.trackingMode() != ShiftTrackingMode.NONE)
                        .map(TreasuryShiftPolicy::treasuryId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                return new AssignmentEditorData(
                        assignments.listAll(),
                        assignments.listHistory(),
                        users.getUsersList().stream().filter(Users::isActive).toList(),
                        treasuries.getActiveTreasuryModelList().stream()
                                .filter(item -> trackedIds.contains(item.getId())).toList());
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((data, error) -> Platform.runLater(() -> {
            setAssignmentBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.assignment.error.load"), rootCause(error));
                return;
            }
            assignmentTable.setItems(FXCollections.observableArrayList(data.assignments()));
            assignmentHistoryTable.setItems(FXCollections.observableArrayList(data.history()));
            comboAssignmentUser.setItems(FXCollections.observableArrayList(data.users()));
            comboAssignmentTreasury.setItems(FXCollections.observableArrayList(data.treasuries()));
            comboAssignmentUser.getSelectionModel().selectFirst();
            comboAssignmentTreasury.getSelectionModel().selectFirst();
        }));
    }

    private void assignTreasury() {
        Users user = comboAssignmentUser.getValue();
        Treasury treasury = comboAssignmentTreasury.getValue();
        if (user == null || treasury == null) {
            AllAlerts.handleError(message("user.shift.assignment.title"),
                    new UserValidationException(message("user.shift.assignment.error.select")));
            return;
        }
        runAssignmentMutation(() -> assignments.assign(
                        user.getId(), treasury.getId(), checkAssignmentDefault.isSelected()),
                "user.shift.assignment.saved");
    }

    private void deactivateAssignment() {
        CashierTreasuryAssignment selected = assignmentTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.active()) {
            AllAlerts.handleError(message("user.shift.assignment.title"),
                    new UserValidationException(message("user.shift.assignment.error.select.assignment")));
            return;
        }
        if (!AllAlerts.confirm_all(message("user.shift.assignment.deactivate.title"),
                message("user.shift.assignment.deactivate.confirm",
                        selected.username(), selected.treasuryName()))) return;
        runAssignmentMutation(() -> assignments.deactivate(selected.id()),
                "user.shift.assignment.deactivated");
    }

    private void runAssignmentMutation(AssignmentAction action, String successKey) {
        setAssignmentBusy(true);
        CompletableFuture.runAsync(() -> {
            try {
                action.execute();
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((ignored, error) -> Platform.runLater(() -> {
            setAssignmentBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.assignment.title"), rootCause(error));
                return;
            }
            AllAlerts.alertSaveWithMessage(message(successKey));
            refreshAssignments();
        }));
    }

    private void setAssignmentBusy(boolean busy) {
        assignmentProgress.setVisible(busy);
        btnAssignmentRefresh.setDisable(busy);
        btnAssignTreasury.setDisable(busy);
        CashierTreasuryAssignment selected = assignmentTable.getSelectionModel().getSelectedItem();
        btnDeactivateAssignment.setDisable(busy || selected == null || !selected.active());
    }

    private String assignmentState(CashierTreasuryAssignmentEvent event) {
        return message("user.shift.assignment.history.state",
                message(event.afterCanOpenShift() ? "yes" : "no"),
                message(event.afterDefaultTreasury() ? "yes" : "no"),
                message(event.afterActive()
                        ? "user.shift.assignment.status.active"
                        : "user.shift.assignment.status.inactive"));
    }

    private void setupHandovers() {
        handoverPolicyTable.getColumns().addAll(
                Columns.text("user.shift.handover.column.source", ShiftCashHandoverPolicy::sourceTreasuryName),
                Columns.text("user.shift.handover.column.target", ShiftCashHandoverPolicy::targetTreasuryName),
                Columns.text("user.shift.handover.column.enabled", row -> message(row.enabled() ? "yes" : "no")),
                Columns.number("user.shift.handover.column.float", ShiftCashHandoverPolicy::retainedFloat),
                Columns.text("user.shift.handover.column.updated.by", ShiftCashHandoverPolicy::updatedByUsername),
                Columns.text("user.shift.handover.column.updated.at", row -> formatTime(row.updatedAt())));
        handoverTable.getColumns().addAll(
                Columns.number(NamesTables.CODE, ShiftCashHandover::id),
                Columns.number("user.shift.handover.column.shift", ShiftCashHandover::shiftId),
                Columns.text("user.shift.handover.column.cashier", ShiftCashHandover::handedByUsername),
                Columns.text("user.shift.handover.column.source", ShiftCashHandover::sourceTreasuryName),
                Columns.text("user.shift.handover.column.target", ShiftCashHandover::targetTreasuryName),
                Columns.number("user.shift.handover.column.actual", ShiftCashHandover::actualBalance),
                Columns.number("user.shift.handover.column.expected", ShiftCashHandover::expectedBalance),
                Columns.number("user.shift.handover.column.difference", ShiftCashHandover::differenceAmount),
                Columns.number("user.shift.handover.column.float", ShiftCashHandover::retainedFloat),
                Columns.number("user.shift.handover.column.amount", ShiftCashHandover::handoverAmount),
                Columns.text("user.shift.handover.column.open.state", this::handoverOpenState),
                Columns.text("user.shift.handover.column.requested.at", row -> formatTime(row.requestedAt())));
        StringConverter<Treasury> converter = new StringConverter<>() {
            @Override public String toString(Treasury value) {
                return value == null ? "" : value.getName();
            }
            @Override public Treasury fromString(String text) { throw new UnsupportedOperationException(); }
        };
        comboHandoverSource.setConverter(converter);
        comboHandoverTarget.setConverter(converter);
        setTextFormatter(txtHandoverFloat);
        mayManageHandovers = AuthorizationGuard.isGranted(AppPermissions.SHIFT_POLICY_MANAGE);
        mayReceiveHandovers = AuthorizationGuard.isGranted(AppPermissions.SHIFT_FORCE_CLOSE);
        handoverPane.setDisable(!mayManageHandovers && !mayReceiveHandovers);
        comboHandoverSource.setDisable(!mayManageHandovers);
        comboHandoverTarget.setDisable(!mayManageHandovers);
        txtHandoverFloat.setDisable(!mayManageHandovers);
        checkHandoverEnabled.setDisable(!mayManageHandovers);
        btnSaveHandoverPolicy.setOnAction(event -> saveHandoverPolicy());
        btnReceiveHandover.setOnAction(event -> receiveHandover());
        btnApproveHandoverOpen.setOnAction(event -> approveHandoverOpen());
        btnHandoverRefresh.setOnAction(event -> refreshHandovers());
        handoverPolicyTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            if (selected == null) return;
            selectTreasury(comboHandoverSource, selected.sourceTreasuryId());
            selectTreasury(comboHandoverTarget, selected.targetTreasuryId());
            txtHandoverFloat.setText(selected.retainedFloat().toPlainString());
            checkHandoverEnabled.setSelected(selected.enabled());
        });
        handoverTable.getSelectionModel().selectedItemProperty().addListener((observable, old, selected) -> {
            btnReceiveHandover.setDisable(!mayReceiveHandovers || selected == null);
            btnApproveHandoverOpen.setDisable(!mayReceiveHandovers || selected == null
                    || !selected.blocksOpening());
        });
        setHandoverBusy(false);
        if (mayManageHandovers || mayReceiveHandovers) refreshHandovers();
    }

    private void refreshHandovers() {
        long request = ++handoverRequest;
        setHandoverBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                List<ShiftCashHandoverPolicy> policyRows = mayManageHandovers
                        ? handovers.policies() : List.of();
                List<Treasury> treasuryRows = mayManageHandovers
                        ? treasuries.getActiveTreasuryModelList() : List.of();
                List<ShiftCashHandover> pendingRows = mayReceiveHandovers
                        ? handovers.pending() : List.of();
                return new HandoverEditorData(policyRows, treasuryRows, pendingRows);
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((data, error) -> Platform.runLater(() -> {
            if (request != handoverRequest) return;
            setHandoverBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.handover.error.load"), rootCause(error));
                return;
            }
            handoverPolicyTable.setItems(FXCollections.observableArrayList(data.policies()));
            handoverTable.setItems(FXCollections.observableArrayList(data.pending()));
            comboHandoverSource.setItems(FXCollections.observableArrayList(data.treasuries()));
            comboHandoverTarget.setItems(FXCollections.observableArrayList(data.treasuries()));
            comboHandoverSource.getSelectionModel().selectFirst();
            if (data.treasuries().size() > 1) comboHandoverTarget.getSelectionModel().select(1);
        }));
    }

    private void saveHandoverPolicy() {
        Treasury source = comboHandoverSource.getValue();
        Treasury target = comboHandoverTarget.getValue();
        if (source == null || target == null) {
            AllAlerts.handleError(message("user.shift.handover.title"),
                    new UserValidationException(message("user.shift.handover.error.treasury")));
            return;
        }
        runHandoverMutation(() -> handovers.savePolicy(source.getId(), target.getId(),
                        parseMoney(txtHandoverFloat.getText()), checkHandoverEnabled.isSelected()),
                "user.shift.handover.saved");
    }

    private void receiveHandover() {
        ShiftCashHandover selected = handoverTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.handleError(message("user.shift.handover.title"),
                    new UserValidationException(message("user.shift.handover.error.select")));
            return;
        }
        if (!AllAlerts.confirm_all(message("user.shift.handover.receive.title"),
                message("user.shift.handover.receive.confirm", selected.handoverAmount(),
                        selected.sourceTreasuryName(), selected.targetTreasuryName()))) return;
        Optional<String> note = promptWithTitle("user.shift.handover.receive.title",
                "user.shift.handover.receive.note", "");
        if (note.isEmpty()) return;
        runHandoverMutation(() -> handovers.receive(selected.id(), note.get()),
                "user.shift.handover.received");
    }

    private void approveHandoverOpen() {
        ShiftCashHandover selected = handoverTable.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.blocksOpening()) {
            AllAlerts.handleError(message("user.shift.handover.override.title"),
                    new UserValidationException(message("user.shift.handover.error.select")));
            return;
        }
        Optional<String> reason = promptWithTitle("user.shift.handover.override.title",
                "user.shift.handover.override.reason", "");
        if (reason.isEmpty()) return;
        if (reason.get().isBlank()) {
            AllAlerts.handleError(message("user.shift.handover.override.title"),
                    new UserValidationException(message(
                            "user.shift.handover.override.reason.required")));
            return;
        }
        if (!AllAlerts.confirm_all(message("user.shift.handover.override.title"),
                message("user.shift.handover.override.confirm",
                        selected.sourceTreasuryName()))) return;
        runHandoverMutation(() -> handovers.approveOpenOverride(selected.id(), reason.get()),
                "user.shift.handover.override.saved");
    }

    private void runHandoverMutation(HandoverAction action, String successKey) {
        setHandoverBusy(true);
        CompletableFuture.runAsync(() -> {
            try {
                action.execute();
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((ignored, error) -> Platform.runLater(() -> {
            setHandoverBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.handover.title"), rootCause(error));
                return;
            }
            AllAlerts.alertSaveWithMessage(message(successKey));
            refreshHandovers();
            refreshData();
        }));
    }

    private void setHandoverBusy(boolean busy) {
        handoverProgress.setVisible(busy);
        btnHandoverRefresh.setDisable(busy);
        btnSaveHandoverPolicy.setDisable(busy || !mayManageHandovers);
        btnReceiveHandover.setDisable(busy || !mayReceiveHandovers
                || handoverTable.getSelectionModel().getSelectedItem() == null);
        ShiftCashHandover selected = handoverTable.getSelectionModel().getSelectedItem();
        btnApproveHandoverOpen.setDisable(busy || !mayReceiveHandovers
                || selected == null || !selected.blocksOpening());
    }

    /**
     * The table is fed by {@code loadPending()} today, so "does not block" always means a
     * supervisor override. It will not always: the moment this table shows received rows,
     * an override name of null would print as the word "null". Read the name, not the state.
     */
    private String handoverOpenState(ShiftCashHandover handover) {
        if (handover.blocksOpening()) return message("user.shift.handover.open.state.blocked");
        String approver = handover.openingOverrideByUsername();
        return approver == null || approver.isBlank()
                ? message("user.shift.handover.open.state.received")
                : message("user.shift.handover.open.state.overridden", approver);
    }

    private static void selectTreasury(ComboBox<Treasury> combo, int treasuryId) {
        combo.getItems().stream().filter(item -> item.getId() == treasuryId)
                .findFirst().ifPresent(combo::setValue);
    }

    private void refreshData() {
        long request = ++dataRequest;
        mainProgress.setVisible(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return shifts.getAllShifts();
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((rows, error) -> Platform.runLater(() -> {
            if (request != dataRequest) return;
            mainProgress.setVisible(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.error.load.title"), rootCause(error));
                return;
            }
            tableView.setItems(FXCollections.observableArrayList(rows));
        }));
        if (AuthorizationGuard.isGranted(AppPermissions.SHIFT_FORCE_CLOSE)) refreshApprovals();
    }

    private void refreshApprovals() {
        long request = ++approvalRequest;
        setApprovalBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return shifts.getPendingCloseRequests();
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((rows, error) -> Platform.runLater(() -> {
            if (request != approvalRequest) return;
            setApprovalBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.approval.error.load"), rootCause(error));
                return;
            }
            approvalTable.setItems(FXCollections.observableArrayList(rows));
        }));
    }

    private void approveSelected() {
        ShiftCloseRequest selected = requireSelectedApproval();
        if (selected == null) return;
        Optional<String> note = promptWithTitle("user.shift.approval.approve.title",
                "user.shift.approval.approve.note", "");
        if (note.isEmpty()) return;
        runDecision(() -> shifts.approveCloseRequest(selected.shiftId(), note.get()),
                "user.shift.approval.approve.title", "user.shift.approval.approved", true);
    }

    private void autoPrintApprovedZ(int shiftId) {
        try {
            if (policies.current().autoPrintZ()) {
                printReports.printShiftZReport(shiftReports.buildApprovedZReport(shiftId));
            }
        } catch (Exception e) {
            log.error("Approved shift {} closed, but its automatic Z report failed", shiftId, e);
        }
    }

    private void rejectSelected() {
        ShiftCloseRequest selected = requireSelectedApproval();
        if (selected == null) return;
        Optional<String> note = promptWithTitle("user.shift.approval.reject.title",
                "user.shift.approval.reject.note", "");
        if (note.isEmpty() || note.get().isBlank()) {
            AllAlerts.handleError(message("user.shift.approval.reject.title"),
                    new UserValidationException(message("user.shift.approval.reject.reason.required")));
            return;
        }
        runDecision(() -> shifts.rejectCloseRequest(selected.shiftId(), note.get()),
                "user.shift.approval.reject.title", "user.shift.approval.rejected", false);
    }

    private void runDecision(DecisionAction action, String titleKey, String successKey, boolean printZ) {
        setApprovalBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                int shiftId = action.execute();
                if (printZ) autoPrintApprovedZ(shiftId);
                return shiftId;
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((shiftId, error) -> Platform.runLater(() -> {
            setApprovalBusy(false);
            if (error != null) {
                AllAlerts.handleError(message(titleKey), rootCause(error));
                return;
            }
            AllAlerts.alertSaveWithMessage(message(successKey));
            refreshData();
        }));
    }

    private void setApprovalBusy(boolean busy) {
        approvalProgress.setVisible(busy);
        btnApprovalRefresh.setDisable(busy || !mayDecideClose);
        btnApproveClose.setDisable(busy || !mayDecideClose);
        btnRejectClose.setDisable(busy || !mayDecideClose);
    }

    private ShiftCloseRequest requireSelectedApproval() {
        ShiftCloseRequest selected = approvalTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AllAlerts.handleError(message("user.shift.approval.title"),
                    new UserValidationException(message("user.shift.approval.select")));
        }
        return selected;
    }

    private Optional<String> promptWithTitle(String titleKey, String headerKey, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(message(titleKey));
        dialog.setHeaderText(message(headerKey));
        return dialog.showAndWait();
    }

    private void forceCloseSelected() {
        try {
            UserShift selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) throw new UserValidationException(message("user.shift.msg.select.first"));
            if (!selected.isOpen()) throw new BusinessRuleException(message("user.shift.msg.already.closed"));
            Optional<String> balance = prompt("user.shift.force.close.balance.prompt", selected.getOpenBalance().toPlainString());
            if (balance.isEmpty()) return;
            Optional<String> reason = prompt("user.shift.force.close.reason.prompt", "");
            if (reason.isEmpty() || reason.get().isBlank()) {
                throw new UserValidationException(message("user.shift.error.variance.reason"));
            }
            BigDecimal actual = parseMoney(balance.get());
            if (!AllAlerts.confirm_all(message("user.shift.force.close.title"), buildForceCloseMessage(selected))) return;
            int result = shifts.forceCloseShift(selected.getId(), actual,
                    reason.get().trim() + " [" + CurrentUser.get().getUsername() + "]");
            if (result > 0) {
                AllAlerts.alertSaveWithMessage(message("user.shift.msg.force.close.success"));
                refreshData();
            }
        } catch (Exception e) {
            AllAlerts.handleError(message("user.shift.error.force.close.title"), e);
        }
    }

    private Optional<String> prompt(String key, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(message("user.shift.force.close.title"));
        dialog.setHeaderText(message(key));
        return dialog.showAndWait();
    }

    private String buildForceCloseMessage(UserShift shift) {
        return String.format(message("user.shift.force.close.confirm"), shift.getUsername(), shift.getId(),
                formatTime(shift.getOpenTime()), shift.getOpenBalance(), shift.getNotes() == null ? "" : shift.getNotes());
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim().replace('٫', '.').replace(',', '.').replaceAll("\\s+", ""));
    }

    private static String formatTime(java.time.LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMATTER);
    }

    private static String message(String key) { return LanguageManager.getInstance().getString(key); }
    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }

    private static <E extends Enum<E>> StringConverter<E> enumConverter(String prefix) {
        return new StringConverter<>() {
            @Override public String toString(E value) {
                return value == null ? "" : message(prefix + value.name().toLowerCase());
            }
            @Override public E fromString(String text) { throw new UnsupportedOperationException(); }
        };
    }

    @FunctionalInterface
    private interface DecisionAction {
        int execute() throws DaoException;
    }

    @FunctionalInterface
    private interface AssignmentAction {
        void execute() throws DaoException;
    }

    @FunctionalInterface
    private interface HandoverAction {
        void execute() throws DaoException;
    }

    private record AssignmentEditorData(
            List<CashierTreasuryAssignment> assignments,
            List<CashierTreasuryAssignmentEvent> history,
            List<Users> users,
            List<Treasury> treasuries) {
    }

    private record HandoverEditorData(
            List<ShiftCashHandoverPolicy> policies,
            List<Treasury> treasuries,
            List<ShiftCashHandover> pending) {
    }
}
