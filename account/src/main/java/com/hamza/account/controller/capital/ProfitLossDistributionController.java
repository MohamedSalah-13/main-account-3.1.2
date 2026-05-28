package com.hamza.account.controller.capital;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Capital;
import com.hamza.account.model.domain.ProfitLossDistribution;
import com.hamza.account.session.UserSession;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.extern.log4j.Log4j2;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Log4j2
public class ProfitLossDistributionController implements Initializable {

    // ===== Distribution Table =====
    @FXML private TableView<ProfitLossDistribution> distributionTable;
    @FXML private TableColumn<ProfitLossDistribution, Integer> idColumn;
    @FXML private TableColumn<ProfitLossDistribution, String> capitalNameColumn;
    @FXML private TableColumn<ProfitLossDistribution, LocalDate> distributionDateColumn;
    @FXML private TableColumn<ProfitLossDistribution, LocalDate> periodFromColumn;
    @FXML private TableColumn<ProfitLossDistribution, LocalDate> periodToColumn;
    @FXML private TableColumn<ProfitLossDistribution, Double> revenueColumn;
    @FXML private TableColumn<ProfitLossDistribution, Double> expensesColumn;
    @FXML private TableColumn<ProfitLossDistribution, Double> netColumn;
    @FXML private TableColumn<ProfitLossDistribution, String> typeColumn;
    @FXML private TableColumn<ProfitLossDistribution, String> statusColumn;

    // ===== Form Fields =====
    @FXML private ComboBox<Capital> capitalComboBox;
    @FXML private DatePicker distributionDatePicker;
    @FXML private DatePicker periodFromPicker;
    @FXML private DatePicker periodToPicker;
    @FXML private TextField totalRevenueField;
    @FXML private TextField totalExpensesField;
    @FXML private TextField netProfitLossField;
    @FXML private RadioButton profitRadio;
    @FXML private RadioButton lossRadio;
    @FXML private ToggleGroup profitLossGroup;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextArea notesArea;

    // ===== Buttons =====
    @FXML private Button calculateBtn;
    @FXML private Button saveBtn;
    @FXML private Button updateBtn;
    @FXML private Button deleteBtn;
    @FXML private Button distributeBtn;
    @FXML private Button newBtn;
    @FXML private Button viewDetailsBtn;

    // ===== Summary Labels =====
    @FXML private Label totalDistributionsLabel;
    @FXML private Label totalProfitLabel;
    @FXML private Label totalLossLabel;
    @FXML private Label pendingCountLabel;

    // Data
    private final ObservableList<ProfitLossDistribution> distributionList = FXCollections.observableArrayList();
    private DaoFactory daoFactory;
    private ProfitLossDistribution selectedDistribution;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            daoFactory = DaoFactory.getInstance();
            setupTable();
            setupForm();
            loadData();
            setupEventHandlers();
            updateSummary();
            setFormState(FormState.NEW);
        } catch (Exception e) {
            log.error("خطأ في تهيئة الشاشة", e);
            AllAlerts.alertError("خطأ في تهيئة الشاشة: " + e.getMessage());
        }
    }

    private void setupTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        capitalNameColumn.setCellValueFactory(new PropertyValueFactory<>("capitalName"));
        distributionDateColumn.setCellValueFactory(new PropertyValueFactory<>("distributionDate"));
        periodFromColumn.setCellValueFactory(new PropertyValueFactory<>("periodFrom"));
        periodToColumn.setCellValueFactory(new PropertyValueFactory<>("periodTo"));
        revenueColumn.setCellValueFactory(new PropertyValueFactory<>("totalRevenue"));
        expensesColumn.setCellValueFactory(new PropertyValueFactory<>("totalExpenses"));
        netColumn.setCellValueFactory(new PropertyValueFactory<>("netProfitLoss"));
        
        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isIsProfit() ? "ربح" : "خسارة")
        );
        
        statusColumn.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getDistributionStatus();
            String statusAr = switch (status) {
                case "PENDING" -> "معلق";
                case "DISTRIBUTED" -> "موزع";
                case "CANCELLED" -> "ملغي";
                default -> status;
            };
            return new SimpleStringProperty(statusAr);
        });

        // Color coding for type
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("ربح")) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        distributionTable.setItems(distributionList);
        distributionTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        selectDistribution(newSelection);
                    }
                }
        );
    }

    private void setupForm() {
        // Setup Capital ComboBox
        capitalComboBox.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Capital item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCapitalName());
                }
            }
        });
        capitalComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Capital item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getCapitalName());
                }
            }
        });

        // Setup Status ComboBox
        statusComboBox.setItems(FXCollections.observableArrayList(
                "PENDING", "DISTRIBUTED", "CANCELLED"
        ));
        statusComboBox.setValue("PENDING");

        // Setup Radio Group
        profitLossGroup = new ToggleGroup();
        profitRadio.setToggleGroup(profitLossGroup);
        lossRadio.setToggleGroup(profitLossGroup);
        profitRadio.setSelected(true);

        // Auto calculate net
        totalRevenueField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculateNet());
        totalExpensesField.textProperty().addListener((obs, oldVal, newVal) -> autoCalculateNet());

        // Set default dates
        distributionDatePicker.setValue(LocalDate.now());
        periodToPicker.setValue(LocalDate.now());
        periodFromPicker.setValue(LocalDate.now().minusMonths(1));
    }

    private void setupEventHandlers() {
        calculateBtn.setOnAction(e -> calculateProfitLoss());
        saveBtn.setOnAction(e -> save());
        updateBtn.setOnAction(e -> update());
        deleteBtn.setOnAction(e -> delete());
        distributeBtn.setOnAction(e -> distribute());
        newBtn.setOnAction(e -> newDistribution());
        viewDetailsBtn.setOnAction(e -> viewDetails());
    }

    private void loadData() {
        loadCapitals();
        loadDistributions();
    }

    private void loadCapitals() {
        try {
            List<Capital> capitals = daoFactory.capitalDao().getActiveCapitals();
            capitalComboBox.setItems(FXCollections.observableArrayList(capitals));
        } catch (DaoException e) {
            log.error("خطأ في تحميل رأس المال", e);
            AllAlerts.alertError("خطأ في تحميل رأس المال: " + e.getMessage());
        }
    }

    private void loadDistributions() {
        try {
            distributionList.clear();
            List<ProfitLossDistribution> distributions = daoFactory.profitLossDistributionDao().loadAll();
            distributionList.addAll(distributions);
            updateSummary();
        } catch (DaoException e) {
            log.error("خطأ في تحميل التوزيعات", e);
            AllAlerts.alertError("خطأ في تحميل التوزيعات: " + e.getMessage());
        }
    }

    private void calculateProfitLoss() {
        try {
            if (!validateCalculateForm()) return;

            Capital capital = capitalComboBox.getValue();
            LocalDate from = periodFromPicker.getValue();
            LocalDate to = periodToPicker.getValue();

            ProfitLossDistribution calculated = daoFactory.profitLossDistributionDao()
                    .calculateProfitLoss(capital.getId(), from, to);

            // Fill form with calculated values
            totalRevenueField.setText(String.format("%.2f", calculated.getTotalRevenue()));
            totalExpensesField.setText(String.format("%.2f", calculated.getTotalExpenses()));
            autoCalculateNet();

            AllAlerts.showSuccessAlert("تم حساب الأرباح/الخسائر بنجاح");
        } catch (Exception e) {
            log.error("خطأ في حساب الأرباح/الخسائر", e);
            AllAlerts.alertError("خطأ في حساب الأرباح/الخسائر: " + e.getMessage());
        }
    }

    private void save() {
        try {
            if (!validateForm()) return;

            ProfitLossDistribution distribution = new ProfitLossDistribution();
            fillDistributionFromForm(distribution);
            distribution.setUserId(UserSession.getInstance().getUser().getId());

            int result = daoFactory.profitLossDistributionDao().insert(distribution);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم حفظ التوزيع بنجاح");
                loadDistributions();
                clearForm();
            }
        } catch (Exception e) {
            log.error("خطأ في حفظ التوزيع", e);
            AllAlerts.alertError("خطأ في حفظ التوزيع: " + e.getMessage());
        }
    }

    private void update() {
        try {
            if (selectedDistribution == null) {
                AllAlerts.alertWarning("الرجاء اختيار توزيع للتعديل");
                return;
            }

            if (!validateForm()) return;

            fillDistributionFromForm(selectedDistribution);

            int result = daoFactory.profitLossDistributionDao().update(selectedDistribution);
            if (result > 0) {
                AllAlerts.showSuccessAlert("تم تعديل التوزيع بنجاح");
                loadDistributions();
                clearForm();
            }
        } catch (Exception e) {
            log.error("خطأ في تعديل التوزيع", e);
            AllAlerts.alertError("خطأ في تعديل التوزيع: " + e.getMessage());
        }
    }

    private void delete() {
        try {
            if (selectedDistribution == null) {
                AllAlerts.alertWarning("الرجاء اختيار توزيع للحذف");
                return;
            }

            if (!selectedDistribution.getDistributionStatus().equals("PENDING")) {
                AllAlerts.alertWarning("لا يمكن حذف توزيع تم تنفيذه");
                return;
            }

            Optional<ButtonType> result = AllAlerts.showConfirmation(
                    "تأكيد الحذف",
                    "هل أنت متأكد من حذف التوزيع؟"
            );

            if (result.isPresent() && result.get() == ButtonType.OK) {
                int deleteResult = daoFactory.profitLossDistributionDao().deleteById(selectedDistribution.getId());
                if (deleteResult > 0) {
                    AllAlerts.showSuccessAlert("تم حذف التوزيع بنجاح");
                    loadDistributions();
                    clearForm();
                }
            }
        } catch (Exception e) {
            log.error("خطأ في حذف التوزيع", e);
            AllAlerts.alertError("خطأ في حذف التوزيع: " + e.getMessage());
        }
    }

    private void distribute() {
        try {
            if (selectedDistribution == null) {
                AllAlerts.alertWarning("الرجاء اختيار توزيع للتنفيذ");
                return;
            }

            if (!selectedDistribution.getDistributionStatus().equals("PENDING")) {
                AllAlerts.alertWarning("هذا التوزيع تم تنفيذه مسبقاً");
                return;
            }

            Optional<ButtonType> result = AllAlerts.showConfirmation(
                    "تأكيد التوزيع",
                    "سيتم توزيع " + (selectedDistribution.isIsProfit() ? "الأرباح" : "الخسائر") +
                            " على الشركاء حسب نسبهم.\n\nهل تريد المتابعة؟"
            );

            if (result.isPresent() && result.get() == ButtonType.OK) {
                daoFactory.profitLossDistributionDao().distributeProfit(
                        selectedDistribution.getId(),
                        UserSession.getInstance().getUser().getId()
                );

                AllAlerts.showSuccessAlert("تم توزيع " + 
                        (selectedDistribution.isIsProfit() ? "الأرباح" : "الخسائر") + " بنجاح");
                loadDistributions();
                clearForm();
            }
        } catch (Exception e) {
            log.error("خطأ في توزيع الأرباح/الخسائر", e);
            AllAlerts.alertError("خطأ في توزيع الأرباح/الخسائر: " + e.getMessage());
        }
    }

    private void viewDetails() {
        if (selectedDistribution == null) {
            AllAlerts.alertWarning("الرجاء اختيار توزيع لعرض التفاصيل");
            return;
        }

        // TODO: Open details dialog showing partner shares
        AllAlerts.showSuccessAlert("سيتم فتح نافذة تفاصيل التوزيع على الشركاء");
    }

    private void selectDistribution(ProfitLossDistribution distribution) {
        this.selectedDistribution = distribution;

        // Find and select capital
        capitalComboBox.getItems().stream()
                .filter(c -> c.getId() == distribution.getCapitalId())
                .findFirst()
                .ifPresent(capitalComboBox::setValue);

        distributionDatePicker.setValue(distribution.getDistributionDate());
        periodFromPicker.setValue(distribution.getPeriodFrom());
        periodToPicker.setValue(distribution.getPeriodTo());
        totalRevenueField.setText(String.valueOf(distribution.getTotalRevenue()));
        totalExpensesField.setText(String.valueOf(distribution.getTotalExpenses()));
        netProfitLossField.setText(String.valueOf(distribution.getNetProfitLoss()));
        
        if (distribution.isIsProfit()) {
            profitRadio.setSelected(true);
        } else {
            lossRadio.setSelected(true);
        }
        
        statusComboBox.setValue(distribution.getDistributionStatus());
        notesArea.setText(distribution.getNotes());

        setFormState(FormState.EDIT);
    }

    private void newDistribution() {
        clearForm();
        setFormState(FormState.NEW);
    }

    private void clearForm() {
        selectedDistribution = null;
        capitalComboBox.setValue(null);
        distributionDatePicker.setValue(LocalDate.now());
        periodFromPicker.setValue(LocalDate.now().minusMonths(1));
        periodToPicker.setValue(LocalDate.now());
        totalRevenueField.clear();
        totalExpensesField.clear();
        netProfitLossField.clear();
        profitRadio.setSelected(true);
        statusComboBox.setValue("PENDING");
        notesArea.clear();
        distributionTable.getSelectionModel().clearSelection();
    }

    private void fillDistributionFromForm(ProfitLossDistribution distribution) {
        distribution.setCapitalId(capitalComboBox.getValue().getId());
        distribution.setDistributionDate(distributionDatePicker.getValue());
        distribution.setPeriodFrom(periodFromPicker.getValue());
        distribution.setPeriodTo(periodToPicker.getValue());
        distribution.setTotalRevenue(Double.parseDouble(totalRevenueField.getText()));
        distribution.setTotalExpenses(Double.parseDouble(totalExpensesField.getText()));
        distribution.setNetProfitLoss(Double.parseDouble(netProfitLossField.getText()));
        distribution.setIsProfit(profitRadio.isSelected());
        distribution.setDistributionStatus(statusComboBox.getValue());
        distribution.setNotes(notesArea.getText());
    }

    private void autoCalculateNet() {
        try {
            if (!totalRevenueField.getText().trim().isEmpty() && 
                !totalExpensesField.getText().trim().isEmpty()) {
                
                double revenue = Double.parseDouble(totalRevenueField.getText());
                double expenses = Double.parseDouble(totalExpensesField.getText());
                double net = revenue - expenses;
                
                netProfitLossField.setText(String.format("%.2f", Math.abs(net)));
                
                if (net >= 0) {
                    profitRadio.setSelected(true);
                    netProfitLossField.setStyle("-fx-text-fill: green;");
                } else {
                    lossRadio.setSelected(true);
                    netProfitLossField.setStyle("-fx-text-fill: red;");
                }
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
    }

    private void updateSummary() {
        try {
            int total = distributionList.size();
            
            double totalProfit = distributionList.stream()
                    .filter(ProfitLossDistribution::isIsProfit)
                    .mapToDouble(ProfitLossDistribution::getNetProfitLoss)
                    .sum();
            
            double totalLoss = distributionList.stream()
                    .filter(d -> !d.isIsProfit())
                    .mapToDouble(ProfitLossDistribution::getNetProfitLoss)
                    .sum();
            
            long pendingCount = distributionList.stream()
                    .filter(d -> "PENDING".equals(d.getDistributionStatus()))
                    .count();

            totalDistributionsLabel.setText(String.valueOf(total));
            totalProfitLabel.setText(String.format("%.2f", totalProfit));
            totalLossLabel.setText(String.format("%.2f", totalLoss));
            pendingCountLabel.setText(String.valueOf(pendingCount));
            
            // Color coding
            totalProfitLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            totalLossLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            if (pendingCount > 0) {
                pendingCountLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            log.error("خطأ في تحديث الملخص", e);
        }
    }

    private boolean validateCalculateForm() {
        if (capitalComboBox.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار رأس المال");
            return false;
        }
        if (periodFromPicker.getValue() == null || periodToPicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء تحديد فترة الحساب");
            return false;
        }
        if (periodFromPicker.getValue().isAfter(periodToPicker.getValue())) {
            AllAlerts.alertWarning("تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
            return false;
        }
        return true;
    }

    private boolean validateForm() {
        if (capitalComboBox.getValue() == null) {
            AllAlerts.alertWarning("الرجاء اختيار رأس المال");
            return false;
        }
        if (distributionDatePicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء تحديد تاريخ التوزيع");
            return false;
        }
        if (periodFromPicker.getValue() == null || periodToPicker.getValue() == null) {
            AllAlerts.alertWarning("الرجاء تحديد فترة التوزيع");
            return false;
        }
        if (totalRevenueField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال إجمالي الإيرادات");
            return false;
        }
        if (totalExpensesField.getText().trim().isEmpty()) {
            AllAlerts.alertWarning("الرجاء إدخال إجمالي المصروفات");
            return false;
        }
        return true;
    }

    private void setFormState(FormState state) {
        switch (state) {
            case NEW:
                saveBtn.setDisable(false);
                updateBtn.setDisable(true);
                deleteBtn.setDisable(true);
                distributeBtn.setDisable(true);
                viewDetailsBtn.setDisable(true);
                break;
            case EDIT:
                saveBtn.setDisable(true);
                updateBtn.setDisable(false);
                deleteBtn.setDisable(false);
                distributeBtn.setDisable(!selectedDistribution.getDistributionStatus().equals("PENDING"));
                viewDetailsBtn.setDisable(false);
                break;
        }
    }

    private enum FormState {
        NEW, EDIT
    }
}
