package com.hamza.account.controller.users;

import com.hamza.account.config.AppIcon;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.events.ShiftsChanged;
import com.hamza.account.features.shift.CashierTreasuryChoice;
import com.hamza.account.features.shift.CashierShiftScreenService;
import com.hamza.account.features.shift.CashierShiftScreenService.CashierShiftScreenData;
import com.hamza.account.features.shift.ShiftCloseAttempt;
import com.hamza.account.features.shift.ShiftStatus;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;
import static com.hamza.controlsfx.others.Utils.whenEnterPressed;

@Log4j2
@FxmlPath(pathFile = "user-shift-view.fxml")
public class UserShiftController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final int currentUserId;
    private final ShiftReportService shiftReportService = ServiceRegistry.get(ShiftReportService.class);
    private final UserShiftService userShiftService = ServiceRegistry.get(UserShiftService.class);
    private final CashierShiftScreenService screenService =
            ServiceRegistry.get(CashierShiftScreenService.class);
    private final EventBus eventBus = ServiceRegistry.get(EventBus.class);
    private final Subscriptions subscriptions = new Subscriptions();

    private final Print_Reports printReports = new Print_Reports();
    @FXML
    private Label labelTitle, labelShiftStatus, labelOpenTime, labelOpenBalance, labelShiftTreasury;
    @FXML
    private VBox boxOpenShift, boxCloseShift;
    @FXML
    private ComboBox<CashierTreasuryChoice> comboOpenTreasury;
    @FXML
    private TextField txtOpenBalance, txtCloseBalance;
    @FXML
    private TextArea txtOpenNotes, txtCloseNotes;
    @FXML
    private Button btnOpenShift, btnCloseShift;
    @FXML
    private TableView<UserShift> tableShifts;
    @FXML
    private TableColumn<UserShift, Integer> colId;
    @FXML
    private TableColumn<UserShift, String> colOpenTime, colCloseTime, colStatus;
    @FXML
    private TableColumn<UserShift, Number> colOpenBalance, colCloseBalance;
    @FXML
    private Label labelSummaryTotalSales, labelSummaryReturns, labelSummaryExpenses,
            labelSummaryExpected, labelSummaryDifference, labelSummaryInvoices,
            labelSummaryOtherIn, labelSummaryOtherOut;
    @FXML
    private Button btnPrintXReport;
    @FXML
    private ProgressIndicator shiftProgress;
    private CashierShiftScreenData viewData;
    private long viewRequest;
    private boolean busy;

    public UserShiftController() {
        this.currentUserId = CurrentUser.get().getId();

    }

    @FXML
    public void initialize() {
        setupTreasuryCombo();
        setupTextFormatters();
        setupTableColumns();
        setupActions();
        setupIcons();
        Label placeholder = new Label(message("user.shift.table.empty"));
        placeholder.getStyleClass().add("table-placeholder");
        tableShifts.setPlaceholder(placeholder);
        applyActionState();
        subscribeToRemoteShiftChanges();
        refreshView();
    }

    private void subscribeToRemoteShiftChanges() {
        if (eventBus != null) {
            subscriptions.add(eventBus.subscribe(ShiftsChanged.class, ignored -> refreshView()));
            subscriptions.disposeWith(tableShifts);
        }
    }

    /**
     * The tills a shift can be opened on - the open ones, with the main treasury
     * preselected.
     * <p>
     * A shift did not name a till at all before V22, and every figure it is judged
     * by is filtered by one: in a business with a drawer and an e-wallet, the
     * wallet's collections were being counted into the cash expected in the drawer.
     */
    private void setupTreasuryCombo() {
        comboOpenTreasury.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CashierTreasuryChoice value) {
                return value == null ? "" : value.treasuryName();
            }
            @Override public CashierTreasuryChoice fromString(String text) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private void setupTextFormatters() {
        setTextFormatter(txtOpenBalance, txtCloseBalance);
    }

    private void setupTableColumns() {
        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getId()).asObject());

        colOpenTime.setCellValueFactory(c -> {
            var t = c.getValue().getOpenTime();
            return new SimpleStringProperty(t != null ? t.format(DATE_TIME_FORMATTER) : "-");
        });

        colCloseTime.setCellValueFactory(c -> {
            var t = c.getValue().getCloseTime();
            return new SimpleStringProperty(t != null ? t.format(DATE_TIME_FORMATTER) : "-");
        });

        // استخدام الـ properties الأصلية من الدومين (ربط حقيقي لا نسخة جديدة)
        colOpenBalance.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getOpenBalance()));
        colCloseBalance.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getCloseBalance()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(localizedStatus(c.getValue())));
    }

    private void setupActions() {
        btnOpenShift.setOnAction(e -> openShift());
        btnCloseShift.setOnAction(e -> closeShift());
        if (btnPrintXReport != null) {
            btnPrintXReport.setOnAction(e -> printXReport());
        }
        whenEnterPressed(txtOpenBalance, comboOpenTreasury, txtOpenNotes, btnOpenShift);
        whenEnterPressed(txtCloseBalance, txtCloseNotes, btnCloseShift);
    }

    private void setupIcons() {
        btnOpenShift.setGraphic(AppIcon.TREASURY_CASH.graphic());
        btnCloseShift.setGraphic(AppIcon.CONFIRM.graphic());
        btnPrintXReport.setGraphic(AppIcon.PRINT.graphic());
    }

    private void applyActionState() {
        boolean closable = viewData != null && viewData.hasClosableShift();
        boolean hasCurrentShift = viewData != null && viewData.currentShift() != null;
        btnOpenShift.setDisable(busy
                || !AuthorizationGuard.isGranted(AppPermissions.SHIFT_SELF_OPEN)
                || hasCurrentShift || comboOpenTreasury.getItems().isEmpty());
        btnCloseShift.setDisable(busy
                || !AuthorizationGuard.isGranted(AppPermissions.SHIFT_SELF_CLOSE)
                || !closable);
        btnPrintXReport.setVisible(AuthorizationGuard.isGranted(AppPermissions.SHIFT_X_REPORT_VIEW));
        btnPrintXReport.setManaged(btnPrintXReport.isVisible());
        btnPrintXReport.setDisable(busy || !closable);
    }

    private void setBusy(boolean value) {
        busy = value;
        shiftProgress.setVisible(value);
        applyActionState();
    }

    private void refreshView() {
        long request = ++viewRequest;
        setBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return screenService.load(currentUserId);
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((data, error) -> Platform.runLater(() -> {
            if (request != viewRequest) return;
            setBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.error.load.status.title"), rootCause(error));
                return;
            }
            applyViewData(data);
        }));
    }

    private void applyViewData(CashierShiftScreenData data) {
        viewData = data;
        CashierTreasuryChoice previous = comboOpenTreasury.getValue();
        comboOpenTreasury.setItems(FXCollections.observableArrayList(data.treasuryChoices()));
        if (previous != null && data.treasuryChoices().stream()
                .anyMatch(item -> item.treasuryId() == previous.treasuryId())) {
            comboOpenTreasury.getItems().stream()
                    .filter(item -> item.treasuryId() == previous.treasuryId())
                    .findFirst().ifPresent(comboOpenTreasury::setValue);
        } else {
            data.treasuryChoices().stream().filter(CashierTreasuryChoice::defaultTreasury).findFirst()
                    .ifPresentOrElse(comboOpenTreasury::setValue,
                            () -> comboOpenTreasury.getSelectionModel().selectFirst());
        }
        tableShifts.setItems(FXCollections.observableArrayList(data.history()));

        UserShift current = data.currentShift();
        if (current == null) {
            ShiftContext.clear();
            showNoOpenShift();
            boxOpenShift.setDisable(false);
            boxCloseShift.setDisable(true);
            txtCloseBalance.clear();
            txtCloseBalance.setDisable(false);
            txtCloseNotes.clear();
            clearSummaryLabels();
        } else {
            if (data.hasClosableShift()) ShiftContext.setCurrentShift(current);
            else ShiftContext.clear();
            showOpenShiftInfo(current);
            boxOpenShift.setDisable(true);
            boxCloseShift.setDisable(!data.hasClosableShift());
            txtCloseBalance.setDisable(!data.reconcilesCash());
            if (!data.reconcilesCash()) {
                txtCloseBalance.setText(current.getOpenBalance().toPlainString());
            } else if (data.blindClose()) {
                txtCloseBalance.clear();
            } else if (txtCloseBalance.getText() == null || txtCloseBalance.getText().isBlank()) {
                txtCloseBalance.setText(current.getOpenBalance().toPlainString());
            }
            showLiveSummary(data);
        }
        applyActionState();
    }

    private void showOpenShiftInfo(UserShift shift) {
        boolean pending = shift.getStatus() == ShiftStatus.PENDING_CLOSE;
        labelShiftStatus.setText(LanguageManager.getInstance().getString(pending
                ? "user.shift.status.pending_close" : "user.shift.status.open"));
        setSemanticStyle(labelShiftStatus, pending ? "info-value" : "success-value");
        labelOpenTime.setText(shift.getOpenTime() != null
                ? shift.getOpenTime().format(DATE_TIME_FORMATTER) : "-");
        labelOpenBalance.setText(String.valueOf(shift.getOpenBalance()));
        labelShiftTreasury.setText(shift.getTreasuryName() == null ? "-" : shift.getTreasuryName());
    }

    private void showNoOpenShift() {
        labelShiftStatus.setText(LanguageManager.getInstance().getString("user.shift.status.none.open"));
        setSemanticStyle(labelShiftStatus, "danger-value");
        labelOpenTime.setText("-");
        labelOpenBalance.setText("0.0");
        labelShiftTreasury.setText("-");
    }

    private void showLiveSummary(CashierShiftScreenData data) {
        ShiftSummary summary = data.summary();
        if (summary == null) {
            clearSummaryLabels();
            return;
        }
        BigDecimal closeBalance = parseBalanceSafe(txtCloseBalance.getText(), summary.getOpenBalance());
        BigDecimal difference = summary.calculateDifference(closeBalance);

        labelSummaryTotalSales.setText(format(summary.getTotalSales()));
        labelSummaryReturns.setText(format(summary.getTotalSalesReturns()));
        labelSummaryExpenses.setText(format(summary.getTotalExpenses()));
        labelSummaryExpected.setText(data.blindClose() ? "-" : format(summary.getExpectedBalance()));
        labelSummaryDifference.setText(data.blindClose() ? "-" : format(difference));
        labelSummaryInvoices.setText(String.valueOf(summary.getInvoicesCount()));
        labelSummaryOtherIn.setText(format(summary.getOtherIn()));
        labelSummaryOtherOut.setText(format(summary.getOtherOut()));
        if (!data.blindClose()) {
            setSemanticStyle(labelSummaryDifference, difference.signum() < 0
                    ? "danger-value" : (difference.signum() > 0 ? "info-value" : "success-value"));
        }
    }

    private void clearSummaryLabels() {
        labelSummaryTotalSales.setText("-");
        labelSummaryReturns.setText("-");
        labelSummaryExpenses.setText("-");
        labelSummaryExpected.setText("-");
        labelSummaryDifference.setText("-");
        labelSummaryInvoices.setText("-");
        labelSummaryOtherIn.setText("-");
        labelSummaryOtherOut.setText("-");
    }

    private String format(BigDecimal v) {
        return String.format("%,.2f", v);
    }

    private BigDecimal parseBalanceSafe(String text, BigDecimal fallback) {
        try {
            return parseBalance(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void openShift() {
        try {
            BigDecimal openBalance = parseBalance(txtOpenBalance.getText());
            if (openBalance.signum() < 0) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.open"),
                        new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.open.balance.negative")));
                return;
            }
            String notes = safeTrim(txtOpenNotes.getText());
            int treasuryId = selectedTreasuryId();
            setBusy(true);
            CompletableFuture.supplyAsync(() -> {
                try {
                    return userShiftService.openShift(currentUserId, treasuryId, openBalance, notes);
                } catch (DaoException e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((shiftId, error) -> Platform.runLater(() -> {
                setBusy(false);
                if (error != null) {
                    AllAlerts.handleError(message("user.shift.open"), rootCause(error));
                    return;
                }
                if (shiftId > 0) {
                    AllAlerts.alertSaveWithMessage(message("user.shift.msg.open.success"));
                    clearOpenShiftFields();
                    refreshView();
                }
            }));
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.open"), e);
        } catch (NumberFormatException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.open"),
                    new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.invalid.balance")));
        }
    }

    /**
     * The till the shift is being opened on. Zero if nothing is chosen, which the
     * service refuses - the picker is a hint and the rule lives where the row is
     * written.
     */
    private int selectedTreasuryId() throws DaoException {
        CashierTreasuryChoice choice = comboOpenTreasury.getSelectionModel().getSelectedItem();
        if (choice == null) {
            throw new UserValidationException(LanguageManager.getInstance().getString(
                    "user.shift.assignment.error.none"));
        }
        return choice.treasuryId();
    }

    private void printXReport() {
        setBusy(true);
        CompletableFuture.runAsync(() -> {
            try {
                var data = shiftReportService.buildXReport(currentUserId);
                printReports.printShiftXReportOrThrow(data);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).whenComplete((ignored, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.error.print.xreport.title"), rootCause(error));
            }
        }));
    }

    private void closeShift() {
        try {
            BigDecimal closeBalance = parseBalance(txtCloseBalance.getText());
            if (closeBalance.signum() < 0) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"),
                        new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.close.balance.negative")));
                return;
            }
            String notes = safeTrim(txtCloseNotes.getText());
            prepareClose(closeBalance, notes);
        } catch (NumberFormatException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"),
                    new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.invalid.balance")));
        }
    }

    private void prepareClose(BigDecimal closeBalance, String notes) {
        setBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                return screenService.load(currentUserId);
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((data, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.close.title"), rootCause(error));
                return;
            }
            applyViewData(data);
            if (!data.hasClosableShift() || data.summary() == null) {
                AllAlerts.handleError(message("user.shift.close.title"),
                        new BusinessRuleException(message("user.shift.msg.no.open.shift")));
                return;
            }
            BigDecimal difference = data.summary().calculateDifference(closeBalance);
            String confirmation = buildCloseConfirmMessage(
                    data.summary(), closeBalance, difference, data.blindClose());
            if (AllAlerts.confirm_all(message("user.shift.close.title"), confirmation)) {
                executeClose(closeBalance, notes, data.autoPrintZ());
            }
        }));
    }

    private void executeClose(BigDecimal closeBalance, String notes, boolean autoPrintZ) {
        setBusy(true);
        CompletableFuture.supplyAsync(() -> {
            try {
                ShiftCloseAttempt attempt = userShiftService.requestCloseShift(
                        currentUserId, closeBalance, notes);
                boolean printed = true;
                if (!attempt.pendingApproval() && autoPrintZ) {
                    try {
                        var zData = shiftReportService.buildOwnZReport(attempt.shiftId(), currentUserId);
                        printReports.printShiftZReportOrThrow(zData);
                    } catch (Exception printError) {
                        printed = false;
                        log.error("Error auto-printing Z-Report", printError);
                    }
                }
                return new CloseUiResult(attempt, printed);
            } catch (DaoException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((result, error) -> Platform.runLater(() -> {
            setBusy(false);
            if (error != null) {
                AllAlerts.handleError(message("user.shift.close.title"), rootCause(error));
                return;
            }
            ShiftContext.clear();
            if (result.attempt().pendingApproval()) {
                AllAlerts.alertSaveWithMessage(message("user.shift.msg.approval.requested"));
            } else {
                AllAlerts.alertSaveWithMessage(message(result.zReportPrinted()
                        ? "user.shift.msg.close.success"
                        : "user.shift.msg.close.success.no.print"));
            }
            clearCloseShiftFields();
            refreshView();
        }));
    }

    private String buildCloseConfirmMessage(ShiftSummary s, BigDecimal closeBalance,
                                            BigDecimal diff, boolean blindClose) {
        if (blindClose) {
            return String.format(LanguageManager.getInstance().getString("user.shift.close.confirm.blind"),
                    s.getTotalSales(), s.getTotalSalesReturns(), s.getTotalExpenses(),
                    s.getOtherIn(), s.getOtherOut(), closeBalance);
        }
        String diffLabel;
        if (diff.abs().compareTo(new BigDecimal("0.005")) < 0) diffLabel = LanguageManager.getInstance().getString("user.shift.diff.matched");
        else if (diff.signum() < 0) diffLabel = String.format(LanguageManager.getInstance().getString("user.shift.diff.shortage"), diff.abs());
        else diffLabel = String.format(LanguageManager.getInstance().getString("user.shift.diff.surplus"), diff);

        return String.format(
                LanguageManager.getInstance().getString("user.shift.close.confirm"),
                s.getTotalSales(),
                s.getTotalSalesReturns(),
                s.getTotalExpenses(),
                // The two lines that were missing entirely: a cashier reading a
                // difference has to be able to see the collections and payments that
                // went through the till, not only the sales.
                s.getOtherIn(),
                s.getOtherOut(),
                s.getExpectedBalance(),
                closeBalance,
                diffLabel);
    }

    private BigDecimal parseBalance(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        // دعم الفواصل العربية والمسافات
        String normalized = text.trim()
                .replace('٫', '.')
                .replace(',', '.')
                .replaceAll("\\s+", "");
        return new BigDecimal(normalized);
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private void clearOpenShiftFields() {
        txtOpenBalance.clear();
        txtOpenNotes.clear();
    }

    private void clearCloseShiftFields() {
        txtCloseBalance.clear();
        txtCloseNotes.clear();
    }

    private String localizedStatus(UserShift shift) {
        return LanguageManager.getInstance().getString("user.shift.status." + shift.getStatus().name().toLowerCase());
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    private record CloseUiResult(ShiftCloseAttempt attempt, boolean zReportPrinted) {
    }

    private static void setSemanticStyle(Label label, String styleClass) {
        label.getStyleClass().removeAll("success-value", "danger-value", "info-value");
        label.getStyleClass().add(styleClass);
    }
}
