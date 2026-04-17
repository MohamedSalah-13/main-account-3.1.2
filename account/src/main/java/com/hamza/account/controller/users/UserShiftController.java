package com.hamza.account.controller.users;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.ShiftReportService;
import com.hamza.account.session.ShiftContext;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.time.format.DateTimeFormatter;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "user-shift-view.fxml")
public class UserShiftController extends ServiceData {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final int currentUserId;
    @FXML
    private Label labelTitle, labelShiftStatus, labelOpenTime, labelOpenBalance;
    @FXML
    private VBox boxOpenShift, boxCloseShift;
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
            labelSummaryExpected, labelSummaryDifference, labelSummaryInvoices;

    @FXML
    private Button btnPrintXReport;

    private final ShiftReportService shiftReportService;
    private final Print_Reports printReports;

    public UserShiftController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
        this.currentUserId = LogApplication.usersVo.getId();
        this.shiftReportService = new ShiftReportService(daoFactory, userShiftService);
        this.printReports = new Print_Reports();
    }

    @FXML
    public void initialize() {
        setupTextFormatters();
        setupTableColumns();
        setupActions();
        refreshView();
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
        colOpenBalance.setCellValueFactory(c -> c.getValue().openBalanceProperty());
        colCloseBalance.setCellValueFactory(c -> c.getValue().closeBalanceProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
    }

    private void setupActions() {

        btnOpenShift.setOnAction(e -> openShift());
        btnCloseShift.setOnAction(e -> closeShift());
        if (btnPrintXReport != null) {
            btnPrintXReport.setOnAction(e -> printXReport());
        }
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
                ShiftContext.setCurrentShift(openShift);
                showOpenShiftInfo(openShift);
                boxOpenShift.setDisable(true);
                boxCloseShift.setDisable(false);
                txtCloseBalance.setText(String.valueOf(openShift.getOpenBalance()));
            } else {
                ShiftContext.clear();
                showNoOpenShift();
                boxOpenShift.setDisable(false);
                boxCloseShift.setDisable(true);
                txtCloseBalance.clear();
                txtCloseNotes.clear();
            }
        } catch (DaoException e) {
            log.error("Error loading shift status", e);
            AllAlerts.alertError("خطأ في تحميل حالة الوردية: " + e.getMessage());
        }
    }

    private void showOpenShiftInfo(UserShift shift) {
        labelShiftStatus.setText("مفتوحة");
        labelShiftStatus.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        labelOpenTime.setText(shift.getOpenTime() != null
                ? shift.getOpenTime().format(DATE_TIME_FORMATTER) : "-");
        labelOpenBalance.setText(String.valueOf(shift.getOpenBalance()));
    }

    private void showNoOpenShift() {
        labelShiftStatus.setText("لا توجد وردية مفتوحة");
        labelShiftStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        labelOpenTime.setText("-");
        labelOpenBalance.setText("0.0");
    }

    private void loadShiftHistory() {
        try {
            var shifts = userShiftService.getUserShifts(currentUserId);
            tableShifts.setItems(FXCollections.observableArrayList(shifts));
        } catch (DaoException e) {
            log.error("Error loading shift history", e);
            AllAlerts.alertError("خطأ في تحميل سجل الورديات: " + e.getMessage());
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
            ShiftSummary s = userShiftService.getCurrentShiftSummary(currentUserId);
            double closeBalance = parseBalanceSafe(txtCloseBalance.getText(), s.getOpenBalance());
            double diff = s.calculateDifference(closeBalance);

            labelSummaryTotalSales.setText(format(s.getTotalSales()));
            labelSummaryReturns.setText(format(s.getTotalSalesReturns()));
            labelSummaryExpenses.setText(format(s.getTotalExpenses()));
            labelSummaryExpected.setText(format(s.getExpectedBalance()));
            labelSummaryDifference.setText(format(diff));
            labelSummaryDifference.setStyle(diff < 0
                    ? "-fx-text-fill: red;  -fx-font-weight: bold;"
                    : (diff > 0 ? "-fx-text-fill: orange; -fx-font-weight: bold;"
                       : "-fx-text-fill: green;  -fx-font-weight: bold;"));
            labelSummaryInvoices.setText(String.valueOf(s.getInvoicesCount()));
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
    }

    private String format(double v) {
        return String.format("%,.2f", v);
    }

    private double parseBalanceSafe(String text, double fallback) {
        try {
            return parseBalance(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void openShift() {
        try {
            double openBalance = parseBalance(txtOpenBalance.getText());
            if (openBalance < 0) {
                AllAlerts.alertError("لا يمكن أن يكون الرصيد الافتتاحي بالسالب!");
                return;
            }
            String notes = safeTrim(txtOpenNotes.getText());

            if (userShiftService.openShift(currentUserId, openBalance, notes) > 0) {
                AllAlerts.alertSaveWithMessage("تم فتح الوردية بنجاح!");
                clearOpenShiftFields();
                refreshView();
            }
        } catch (DaoException e) {
            log.error("Error opening shift", e);
            AllAlerts.alertError(e.getMessage());
        } catch (NumberFormatException e) {
            AllAlerts.alertError("الرجاء إدخال رصيد صحيح!");
        }
    }

    private void printXReport() {
        try {
            var data = shiftReportService.buildXReport(currentUserId);
            printReports.printShiftXReport(data);
        } catch (DaoException e) {
            log.error("Error printing X-Report", e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void closeShift() {
        try {
            if (!userShiftService.hasOpenShift(currentUserId)) {
                AllAlerts.alertError("لا توجد وردية مفتوحة لإغلاقها!");
                return;
            }

            double closeBalance = parseBalance(txtCloseBalance.getText());
            if (closeBalance < 0) {
                AllAlerts.alertError("لا يمكن أن يكون الرصيد الختامي بالسالب!");
                return;
            }

            // عرض ملخص التأكيد قبل الغلق
            ShiftSummary s = userShiftService.getCurrentShiftSummary(currentUserId);
            double diff = s.calculateDifference(closeBalance);
            String msg = buildCloseConfirmMessage(s, closeBalance, diff);
            if (!AllAlerts.confirm_all(msg)) {
                return;
            }

            String notes = safeTrim(txtCloseNotes.getText());
            int closedShiftId = userShiftService.closeShift(currentUserId, closeBalance, notes);
            if (closedShiftId > 0) {
                ShiftContext.clear();
                // طباعة Z-Report تلقائياً
                try {
                    var zData = shiftReportService.buildZReport(closedShiftId);
                    printReports.printShiftZReport(zData);
                } catch (Exception ex) {
                    log.error("Error auto-printing Z-Report", ex);
                }
                AllAlerts.alertSaveWithMessage("تم غلق الوردية بنجاح!");
                clearCloseShiftFields();
                refreshView();
            }
        } catch (DaoException e) {
            log.error("Error closing shift", e);
            AllAlerts.alertError(e.getMessage());
        } catch (NumberFormatException e) {
            AllAlerts.alertError("الرجاء إدخال رصيد صحيح!");
        }
    }

    private String buildCloseConfirmMessage(ShiftSummary s, double closeBalance, double diff) {
        String diffLabel;
        if (Math.abs(diff) < 0.005)         diffLabel = "مطابق ✅";
        else if (diff < 0)                  diffLabel = String.format("عجز %,.2f ⚠️", -diff);
        else                                diffLabel = String.format("زيادة %,.2f", diff);

        return String.format(
                "ملخص الوردية:%n" +
                        "- المبيعات: %,.2f%n" +
                        "- المرتجعات: %,.2f%n" +
                        "- المصروفات: %,.2f%n" +
                        "- الرصيد المتوقع: %,.2f%n" +
                        "- الرصيد المُدخل: %,.2f%n" +
                        "- الفرق: %s%n%n" +
                        "هل تريد غلق الوردية؟",
                s.getTotalSales(),
                s.getTotalSalesReturns(),
                s.getTotalExpenses(),
                s.getExpectedBalance(),
                closeBalance,
                diffLabel);
    }

    private double parseBalance(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        // دعم الفواصل العربية والمسافات
        String normalized = text.trim()
                .replace('٫', '.')
                .replace(',', '.')
                .replaceAll("\\s+", "");
        return Double.parseDouble(normalized);
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
}