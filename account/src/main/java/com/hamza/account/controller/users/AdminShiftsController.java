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
import com.hamza.account.features.shift.ShiftLedgerAction;
import com.hamza.account.features.shift.ShiftReconciliationResult;
import com.hamza.account.features.shift.ShiftReconciliationService;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.UserShiftService;
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
public class AdminShiftsController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final UserShiftService shifts = ServiceRegistry.get(UserShiftService.class);
    private final ShiftPolicyService policies = ServiceRegistry.get(ShiftPolicyService.class);
    private final ShiftCashAuditService audit = ServiceRegistry.get(ShiftCashAuditService.class);
    private final ShiftReconciliationService reconciliation = ServiceRegistry.get(ShiftReconciliationService.class);

    @FXML private TableView<UserShift> tableView;
    @FXML private Button btnRefresh, btnForceClose, btnSavePolicy;
    @FXML private ComboBox<ShiftMode> comboShiftMode;
    @FXML private CheckBox checkBlindClose, checkAutoPrintZ, checkVarianceReason, checkSupervisorApproval;
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
    private long ledgerRequest;
    private long reconciliationRequest;

    @FXML
    public void initialize() {
        setupTable();
        setupActions();
        setupPolicyEditor();
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
        btnForceClose.setDisable(!AuthorizationGuard.isGranted(AppPermissions.SHIFT_FORCE_CLOSE));
        try {
            ShiftPolicy policy = policies.current();
            comboShiftMode.setValue(policy.mode());
            checkBlindClose.setSelected(policy.blindClose());
            checkAutoPrintZ.setSelected(policy.autoPrintZ());
            checkVarianceReason.setSelected(policy.requireVarianceReason());
            checkSupervisorApproval.setSelected(policy.requireSupervisorApproval());
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
                    checkVarianceReason.isSelected(), checkSupervisorApproval.isSelected());
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

    private void refreshData() {
        try {
            tableView.setItems(FXCollections.observableArrayList(shifts.getAllShifts()));
        } catch (DaoException e) {
            AllAlerts.handleError(message("user.shift.error.load.title"), e);
        }
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
}
