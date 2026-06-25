package com.hamza.account.controller.delegates;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegatePerformanceReport;
import com.hamza.account.model.domain.DelegateProfile;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.security.PermissionHelper;
import com.hamza.account.service.DelegateReportService;
import com.hamza.account.service.DelegateService;
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
@FxmlPath(pathFile = "delegates/delegate-performance-report-view.fxml")
public class DelegatePerformanceReportController {

    private static final String ALL_DELEGATES = "كل المندوبين";
    private static final String ALL_AREAS = "كل المناطق";

    private final DelegateReportService delegateReportService = ServiceRegistry.get(DelegateReportService.class);
    private final DelegateService delegateService = ServiceRegistry.get(DelegateService.class);

    @FXML
    private StackPane stackPane;

    @FXML
    private DatePicker dateFrom;

    @FXML
    private DatePicker dateTo;

    @FXML
    private ComboBox<String> comboDelegate;

    @FXML
    private ComboBox<String> comboArea;

    @FXML
    private TextField txtSearch;

    @FXML
    private Button btnSearch;

    @FXML
    private Button btnPrint;

    @FXML
    private Button btnExport;

    @FXML
    private TableView<DelegatePerformanceReport> tablePerformance;

    @FXML
    private Label labelTotalSales;

    @FXML
    private Label labelNetSales;

    @FXML
    private Label labelTotalCollected;

    @FXML
    private Label labelTotalProfit;

    @FXML
    private Label labelInvoicesCount;

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
        PermissionHelper.disableIfNotAllowed(btnSearch, PermissionCode.DELEGATES_REPORTS);
        PermissionHelper.disableIfNotAllowed(btnPrint, PermissionCode.DELEGATES_REPORTS);
        PermissionHelper.disableIfNotAllowed(btnExport, PermissionCode.DELEGATES_REPORTS);
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tablePerformance, DelegatePerformanceReport.class);
        tablePerformance.itemsProperty().addListener((observable, oldValue, newValue) -> updateSummary());
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

        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> applyLocalSearch());

        comboDelegate.valueProperty().addListener((observable, oldValue, newValue) -> loadData());
        comboArea.valueProperty().addListener((observable, oldValue, newValue) -> applyLocalSearch());
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

            List<String> areaNames = delegates.stream()
                    .map(DelegateProfile::getAreaName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .toList();

            comboArea.setItems(FXCollections.observableArrayList());
            comboArea.getItems().add(ALL_AREAS);
            comboArea.getItems().addAll(areaNames);
            comboArea.getSelectionModel().select(ALL_AREAS);

        } catch (DaoException e) {
            logError(e);
        }
    }

    private void loadData() {
        try {
            if (!PermissionHelper.has(PermissionCode.DELEGATES_REPORTS)) {
                AllAlerts.alertWarning("ليس لديك صلاحية عرض تقارير المندوبين");
                return;
            }

            validateDates();

            Integer delegateId = getSelectedDelegateId();

            List<DelegatePerformanceReport> list = delegateReportService.getDailyPerformanceReport(
                    delegateId,
                    dateFrom.getValue(),
                    dateTo.getValue()
            );

            tablePerformance.setItems(FXCollections.observableArrayList(list));
            applyLocalSearch();
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

    private void applyLocalSearch() {
        try {
            Integer delegateId = getSelectedDelegateId();

            List<DelegatePerformanceReport> baseList = delegateReportService.getDailyPerformanceReport(
                    delegateId,
                    dateFrom.getValue(),
                    dateTo.getValue()
            );

            String searchText = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
            String areaName = comboArea.getSelectionModel().getSelectedItem();

            List<DelegatePerformanceReport> filtered = baseList.stream()
                    .filter(row -> searchText.isBlank()
                            || contains(row.getDelegateName(), searchText)
                            || String.valueOf(row.getDelegateId()).contains(searchText)
                    )
                    .filter(row -> areaName == null
                            || ALL_AREAS.equals(areaName)
                            || delegateHasArea(row.getDelegateId(), areaName)
                    )
                    .toList();

            tablePerformance.setItems(FXCollections.observableArrayList(filtered));
            updateSummary();

        } catch (Exception e) {
            logError(e);
        }
    }

    private boolean delegateHasArea(int delegateId, String areaName) {
        try {
            DelegateProfile delegate = delegateService.getDelegateByEmployeeId(delegateId);
            return delegate != null
                    && delegate.getAreaName() != null
                    && delegate.getAreaName().equals(areaName);
        } catch (DaoException e) {
            logError(e);
            return false;
        }
    }

    private void updateSummary() {
        List<DelegatePerformanceReport> list = tablePerformance.getItems();

        double totalSales = delegateReportService.sumGrossSales(list);
        double netSales = delegateReportService.sumNetSales(list);
        double totalCollected = delegateReportService.sumTotalCollected(list);
        double totalProfit = delegateReportService.sumNetProfit(list);
        int invoicesCount = delegateReportService.sumInvoicesCount(list);

        labelTotalSales.setText(formatMoney(totalSales));
        labelNetSales.setText(formatMoney(netSales));
        labelTotalCollected.setText(formatMoney(totalCollected));
        labelTotalProfit.setText(formatMoney(totalProfit));
        labelInvoicesCount.setText(String.valueOf(invoicesCount));
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

    private boolean contains(String source, String searchText) {
        return source != null && source.toLowerCase().contains(searchText);
    }

    private String formatMoney(double value) {
        return String.format("%,.2f", value);
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}
