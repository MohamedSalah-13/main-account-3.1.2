package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.service.UserShiftService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.shift.CashierTreasuryAssignmentService;
import com.hamza.account.features.shift.CashierTreasuryChoice;
import com.hamza.account.features.shift.ShiftPolicyService;
import com.hamza.account.features.shift.ShiftCloseAttempt;
import com.hamza.account.features.shift.ShiftStatus;
import com.hamza.account.features.shift.ShiftTrackingMode;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;

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
    private final ShiftPolicyService shiftPolicyService = ServiceRegistry.get(ShiftPolicyService.class);
    private final CashierTreasuryAssignmentService treasuryAssignments =
            ServiceRegistry.get(CashierTreasuryAssignmentService.class);

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

    public UserShiftController() {
        this.currentUserId = CurrentUser.get().getId();

    }

    @FXML
    public void initialize() {
        loadTreasuries();
        setupTextFormatters();
        setupTableColumns();
        setupActions();
        applyPermissionHints();
        refreshView();
    }

    /**
     * The tills a shift can be opened on - the open ones, with the main treasury
     * preselected.
     * <p>
     * A shift did not name a till at all before V22, and every figure it is judged
     * by is filtered by one: in a business with a drawer and an e-wallet, the
     * wallet's collections were being counted into the cash expected in the drawer.
     */
    private void loadTreasuries() {
        try {
            var choices = treasuryAssignments.availableTreasuries(currentUserId);
            comboOpenTreasury.setItems(FXCollections.observableArrayList(choices));
            comboOpenTreasury.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(CashierTreasuryChoice value) {
                    return value == null ? "" : value.treasuryName();
                }
                @Override public CashierTreasuryChoice fromString(String text) {
                    throw new UnsupportedOperationException();
                }
            });
            choices.stream().filter(CashierTreasuryChoice::defaultTreasury).findFirst()
                    .ifPresentOrElse(comboOpenTreasury::setValue,
                            () -> comboOpenTreasury.getSelectionModel().selectFirst());
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("treasury.error.load.title"), e);
        }
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

    private void applyPermissionHints() {
        btnOpenShift.setDisable(!AuthorizationGuard.isGranted(AppPermissions.SHIFT_SELF_OPEN)
                || comboOpenTreasury.getItems().isEmpty());
        btnCloseShift.setDisable(!AuthorizationGuard.isGranted(AppPermissions.SHIFT_SELF_CLOSE));
        btnPrintXReport.setVisible(AuthorizationGuard.isGranted(AppPermissions.SHIFT_X_REPORT_VIEW));
        btnPrintXReport.setManaged(btnPrintXReport.isVisible());
    }

    private void refreshView() {
        loadCurrentShiftStatus();
        loadShiftHistory();
        loadLiveSummary();
    }

    private void loadCurrentShiftStatus() {
        try {
            if (userShiftService.hasOpenShift(currentUserId)) {
                UserShift openShift = userShiftService.getOpenShift(currentUserId);
                if (openShift.getStatus() == ShiftStatus.OPEN) ShiftContext.setCurrentShift(openShift);
                else ShiftContext.clear();
                btnPrintXReport.setDisable(openShift.getStatus() != ShiftStatus.OPEN);
                showOpenShiftInfo(openShift);
                boxOpenShift.setDisable(true);
                boxCloseShift.setDisable(openShift.getStatus() != ShiftStatus.OPEN);
                txtCloseBalance.setDisable(!isReconcile(openShift.getTreasuryId()));
                if (!isReconcile(openShift.getTreasuryId())) txtCloseBalance.setText(openShift.getOpenBalance().toPlainString());
                else if (blindClose()) txtCloseBalance.clear();
                else txtCloseBalance.setText(openShift.getOpenBalance().toPlainString());
            } else {
                ShiftContext.clear();
                btnPrintXReport.setDisable(true);
                showNoOpenShift();
                boxOpenShift.setDisable(false);
                boxCloseShift.setDisable(true);
                txtCloseBalance.clear();
                txtCloseBalance.setDisable(false);
                txtCloseNotes.clear();
            }
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.error.load.status.title"), e);
        }
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

    private void loadShiftHistory() {
        try {
            var shifts = userShiftService.getUserShifts(currentUserId);
            tableShifts.setItems(FXCollections.observableArrayList(shifts));
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.error.load.history.title"), e);
        }
    }

    /**
     * تحميل الملخص اللحظي (X-Report) لو هناك وردية مفتوحة.
     */
    private void loadLiveSummary() {
        if (labelSummaryTotalSales == null) {
            return; // الحقول غير موجودة في الـ fxml بعد
        }
        try {
            if (!userShiftService.hasOpenShift(currentUserId)) {
                clearSummaryLabels();
                return;
            }
            UserShift current = userShiftService.getOpenShift(currentUserId);
            if (current == null || current.getStatus() != ShiftStatus.OPEN) {
                clearSummaryLabels();
                return;
            }
            ShiftSummary s = userShiftService.getCurrentShiftSummary(currentUserId);
            BigDecimal closeBalance = parseBalanceSafe(txtCloseBalance.getText(), s.getOpenBalance());
            BigDecimal diff = s.calculateDifference(closeBalance);

            labelSummaryTotalSales.setText(format(s.getTotalSales()));
            labelSummaryReturns.setText(format(s.getTotalSalesReturns()));
            labelSummaryExpenses.setText(format(s.getTotalExpenses()));
            labelSummaryExpected.setText(blindClose() ? "-" : format(s.getExpectedBalance()));
            labelSummaryDifference.setText(blindClose() ? "-" : format(diff));
            labelSummaryInvoices.setText(String.valueOf(s.getInvoicesCount()));
            labelSummaryOtherIn.setText(format(s.getOtherIn()));
            labelSummaryOtherOut.setText(format(s.getOtherOut()));
            if (blindClose()) return;
            setSemanticStyle(labelSummaryDifference, diff.signum() < 0
                    ? "danger-value" : (diff.signum() > 0 ? "info-value" : "success-value"));
        } catch (DaoException e) {
            log.error("Error loading live summary", e);
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

            if (userShiftService.openShift(currentUserId, treasuryId, openBalance, notes) > 0) {
                AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("user.shift.msg.open.success"));
                clearOpenShiftFields();
                refreshView();
            }
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
        try {
            var data = shiftReportService.buildXReport(currentUserId);
            printReports.printShiftXReport(data);
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.error.print.xreport.title"), e);
        }
    }

    private void closeShift() {
        try {
            if (!userShiftService.hasOpenShift(currentUserId)) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"),
                        new BusinessRuleException(LanguageManager.getInstance().getString("user.shift.msg.no.open.shift")));
                return;
            }

            BigDecimal closeBalance = parseBalance(txtCloseBalance.getText());
            if (closeBalance.signum() < 0) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"),
                        new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.close.balance.negative")));
                return;
            }

            // عرض ملخص التأكيد قبل الغلق
            ShiftSummary s = userShiftService.getCurrentShiftSummary(currentUserId);
            BigDecimal diff = s.calculateDifference(closeBalance);
            String msg = buildCloseConfirmMessage(s, closeBalance, diff);
            if (!AllAlerts.confirm_all(LanguageManager.getInstance().getString("user.shift.close.title"), msg)) {
                return;
            }

            String notes = safeTrim(txtCloseNotes.getText());
            ShiftCloseAttempt attempt = userShiftService.requestCloseShift(currentUserId, closeBalance, notes);
            if (attempt.pendingApproval()) {
                ShiftContext.clear();
                AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString(
                        "user.shift.msg.approval.requested"));
                clearCloseShiftFields();
                refreshView();
                return;
            }
            int closedShiftId = attempt.shiftId();
            if (closedShiftId > 0) {
                ShiftContext.clear();
                // طباعة Z-Report تلقائياً
                try {
                    if (!shiftPolicyService.current().autoPrintZ()) throw new AutoPrintDisabled();
                    var zData = shiftReportService.buildOwnZReport(closedShiftId, currentUserId);
                    printReports.printShiftZReport(zData);
                } catch (AutoPrintDisabled ignored) {
                    // Explicit policy: closing succeeds without printing.
                } catch (Exception ex) {
                    log.error("Error auto-printing Z-Report", ex);
                }
                AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("user.shift.msg.close.success"));
                clearCloseShiftFields();
                refreshView();
            }
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"), e);
        } catch (NumberFormatException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("user.shift.close.title"),
                    new UserValidationException(LanguageManager.getInstance().getString("user.shift.msg.invalid.balance")));
        }
    }

    private String buildCloseConfirmMessage(ShiftSummary s, BigDecimal closeBalance, BigDecimal diff) {
        if (blindClose()) {
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

    private boolean blindClose() {
        try {
            return shiftPolicyService.current().blindClose();
        } catch (DaoException e) {
            return false;
        }
    }

    private boolean isReconcile(int treasuryId) {
        try {
            return shiftPolicyService.treasuries().stream()
                    .filter(item -> item.treasuryId() == treasuryId)
                    .map(item -> item.trackingMode() == ShiftTrackingMode.RECONCILE)
                    .findFirst().orElse(false);
        } catch (DaoException e) {
            return true;
        }
    }

    private static final class AutoPrintDisabled extends RuntimeException {
    }

    private static void setSemanticStyle(Label label, String styleClass) {
        label.getStyleClass().removeAll("success-value", "danger-value", "info-value");
        label.getStyleClass().add(styleClass);
    }
}
