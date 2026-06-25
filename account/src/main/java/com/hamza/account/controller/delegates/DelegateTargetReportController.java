package com.hamza.account.controller.delegates;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateProfile;
import com.hamza.account.model.domain.DelegateTargetReport;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.service.DelegateReportService;
import com.hamza.account.service.DelegateService;
import com.hamza.account.service.DelegateTargetService;
import com.hamza.account.type.PermissionCode;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "delegates/delegate-target-report-view.fxml")
public class DelegateTargetReportController {

    private static final String ALL_DELEGATES = "كل المندوبين";
    private static final String ALL_TYPES = "كل الأنواع";
    private static final String ALL_STATUSES = "كل الحالات";

    private final DelegateReportService delegateReportService = ServiceRegistry.get(DelegateReportService.class);
    private final DelegateService delegateService = ServiceRegistry.get(DelegateService.class);
    private final DelegateTargetService delegateTargetService = ServiceRegistry.get(DelegateTargetService.class);

    @FXML
    private StackPane stackPane;

    @FXML
    private DatePicker dateFrom;

    @FXML
    private DatePicker dateTo;

    @FXML
    private ComboBox<String> comboDelegate;

    @FXML
    private ComboBox<String> comboTargetType;

    @FXML
    private ComboBox<String> comboStatus;

    @FXML
    private Button btnSearch;

    @FXML
    private Button btnPrint;

    @FXML
    private Button btnExport;

    @FXML
    private TableView<DelegateTargetReport> tableTargetReport;

    @FXML
    private Label labelTargetsCount;

    @FXML
    private Label labelAchievedCount;

    @FXML
    private Label labelInProgressCount;

    @FXML
    private Label labelAverageAchievement;

    @FXML
    public void initialize() {
        applyPermissions();
        setupTable();
        setupInputs();
        setupActions();
        loadComboData();
        loadData();
    }

    private void applyPermissions() {
        PermissionHelper.disableIfNotAllowed(btnSearch, PermissionCode.TARGETS_REPORTS);
        PermissionHelper.disableIfNotAllowed(btnPrint, PermissionCode.TARGETS_REPORTS);
        PermissionHelper.disableIfNotAllowed(btnExport, PermissionCode.TARGETS_REPORTS);
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableTargetReport, DelegateTargetReport.class);
        tableTargetReport.itemsProperty().addListener((observable, oldValue, newValue) -> updateSummary());
    }

    private void setupInputs() {
        LocalDate now = LocalDate.now();
        dateFrom.setValue(now.withDayOfMonth(1));
        dateTo.setValue(now);
    }

    private void setupActions() {
        btnSearch.setOnAction(event -> loadData());

        btnPrint.setOnAction(event -> {
            AllAlerts.alertError("سيتم ربط الطباعة لاحقًا");
        });

        btnExport.setOnAction(event -> {
            AllAlerts.alertError("سيتم ربط التصدير لاحقًا");
        });

        comboDelegate.valueProperty().addListener((observable, oldValue, newValue) -> loadData());
        comboTargetType.valueProperty().addListener((observable, oldValue, newValue) -> applyLocalFilters());
        comboStatus.valueProperty().addListener((observable, oldValue, newValue) -> applyLocalFilters());
    }

    private void loadComboData() {
        try {
            List<DelegateProfile> delegates = delegateService.getAllDelegates();

            List<String> delegateNames = delegates.stream()
                    .map(DelegateProfile::getDelegateName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList();

            comboDelegate.setItems(FXCollections.observableArrayList());
            comboDelegate.getItems().add(ALL_DELEGATES);
            comboDelegate.getItems().addAll(delegateNames);
            comboDelegate.getSelectionModel().select(ALL_DELEGATES);

            comboTargetType.setItems(FXCollections.observableArrayList());
            comboTargetType.getItems().add(ALL_TYPES);
            comboTargetType.getItems().addAll(delegateTargetService.getTargetTypes());
            comboTargetType.getSelectionModel().select(ALL_TYPES);

            comboStatus.setItems(FXCollections.observableArrayList(
                    ALL_STATUSES,
                    "NOT_STARTED",
                    "IN_PROGRESS",
                    "ACHIEVED",
                    "FAILED"
            ));
            comboStatus.getSelectionModel().select(ALL_STATUSES);

        } catch (DaoException e) {
            logError(e);
        }
    }

    private void loadData() {
        try {
            if (!PermissionHelper.has(PermissionCode.TARGETS_REPORTS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض تقارير الأهداف");
                return;
            }

            validateDates();

            Integer delegateId = getSelectedDelegateId();

            List<DelegateTargetReport> list = delegateReportService.getTargetReport(
                    delegateId,
                    dateFrom.getValue(),
                    dateTo.getValue()
            );

            tableTargetReport.setItems(FXCollections.observableArrayList(list));
            applyLocalFilters();
            updateSummary();

        } catch (Exception e) {
            logError(e);
        }
    }

    private Integer getSelectedDelegateId() throws DaoException {
        String delegateName = comboDelegate.getSelectionModel().getSelectedItem();

        if (delegateName == null || delegateName.isBlank() || ALL_DELEGATES.equals(delegateName)) {
            return null;
        }

        DelegateProfile delegate = delegateService.getDelegateByName(delegateName);

        if (delegate == null || delegate.getDelegateId() <= 0) {
            return null;
        }

        return delegate.getDelegateId();
    }

    private void applyLocalFilters() {
        try {
            Integer delegateId = getSelectedDelegateId();

            List<DelegateTargetReport> baseList = delegateReportService.getTargetReport(
                    delegateId,
                    dateFrom.getValue(),
                    dateTo.getValue()
            );

            String selectedType = comboTargetType.getSelectionModel().getSelectedItem();
            String selectedStatus = comboStatus.getSelectionModel().getSelectedItem();

            List<DelegateTargetReport> filtered = baseList.stream()
                    .filter(row -> selectedType == null
                            || ALL_TYPES.equals(selectedType)
                            || selectedType.equals(row.getTargetType()))
                    .filter(row -> selectedStatus == null
                            || ALL_STATUSES.equals(selectedStatus)
                            || selectedStatus.equals(row.getAchievementStatus()))
                    .toList();

            tableTargetReport.setItems(FXCollections.observableArrayList(filtered));
            updateSummary();

        } catch (Exception e) {
            logError(e);
        }
    }

    private void updateSummary() {
        List<DelegateTargetReport> list = tableTargetReport.getItems();

        int totalTargets = list == null ? 0 : list.size();
        long achievedCount = delegateReportService.countAchievedTargets(list);
        long inProgressCount = delegateReportService.countInProgressTargets(list);
        double averagePercent = delegateReportService.averageAchievementPercent(list);

        labelTargetsCount.setText(String.valueOf(totalTargets));
        labelAchievedCount.setText(String.valueOf(achievedCount));
        labelInProgressCount.setText(String.valueOf(inProgressCount));
        labelAverageAchievement.setText(formatPercent(averagePercent));
    }

    private void validateDates() throws Exception {
        if (dateFrom.getValue() == null) {
            throw new Exception("من فضلك اختر تاريخ البداية");
        }

        if (dateTo.getValue() == null) {
            throw new Exception("من فضلك اختر تاريخ النهاية");
        }

        if (dateTo.getValue().isBefore(dateFrom.getValue())) {
            throw new Exception("تاريخ النهاية يجب أن يكون أكبر من أو يساوي تاريخ البداية");
        }
    }

    private String formatPercent(double value) {
        return String.format("%,.2f%%", value);
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}