package com.hamza.account.controller.reports;

import com.hamza.account.model.domain.CustomerStatementRow;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.controlsfx.alert.AllAlerts;
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
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;

@Log4j2
public class CustomerStatementController implements Initializable {

    @FXML private Label lblCustomerName, lblCustomerPhone, lblCurrentBalance;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private TableView<CustomerStatementRow> tableView;
    @FXML private TableColumn<CustomerStatementRow, String> colDate, colType, colRef;
    @FXML private TableColumn<CustomerStatementRow, Double> colDebit, colCredit, colBalance;

    private DaoFactory daoFactory;
    private CustomerReceivable currentCustomer;
    private final ObservableList<CustomerStatementRow> statementData = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        dpFrom.setValue(LocalDate.now().minusMonths(1)); // افتراضياً آخر شهر
        dpTo.setValue(LocalDate.now());
    }

    private void setupColumns() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colRef.setCellValueFactory(new PropertyValueFactory<>("reference"));
        colDebit.setCellValueFactory(new PropertyValueFactory<>("debit"));
        colCredit.setCellValueFactory(new PropertyValueFactory<>("credit"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));

        formatCurrencyColumn(colDebit);
        formatCurrencyColumn(colCredit);
        formatCurrencyColumn(colBalance);
    }

    /**
     * يتم استدعاء هذه الدالة من شاشة "ديون العملاء" عند الضغط مرتين على عميل
     */
    public void loadCustomerData(CustomerReceivable customer, DaoFactory daoFactory) {
        this.currentCustomer = customer;
        this.daoFactory = daoFactory;

        lblCustomerName.setText("اسم العميل: " + customer.getCustomerName());
        lblCustomerPhone.setText("هاتف: " + customer.getCustomerPhone());
        lblCurrentBalance.setText(String.format("%,.2f", customer.getTotalReceivable()));

        onSearchAction();
    }

    @FXML
    private void onSearchAction() {
        if (currentCustomer == null) return;

        try {
            // جلب الحركات (مبيعات، مرتجعات، دفعات) من الـ DAO
//            List<CustomerStatementRow> rows = daoFactory.customerDao()
//                    .getCustomerStatement(currentCustomer.getCustomerId(), dpFrom.getValue(), dpTo.getValue());
//
//            // حساب الرصيد المتراكم برمجياً
//            double runningBalance = 0; // يمكن البدء برصيد ما قبل الفترة إذا أردت
//            for (CustomerStatementRow row : rows) {
//                runningBalance += (row.getDebit() - row.getCredit());
//                row.setBalance(runningBalance);
//            }

//            statementData.setAll(rows);
            tableView.setItems(statementData);

        } catch (Exception e) {
            log.error("خطأ في جلب كشف الحساب", e);
            AllAlerts.alertError("فشل تحميل البيانات: " + e.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        if (statementData.isEmpty()) return;
        // استدعاء خدمة تصدير PDF لكشف الحساب
        showInfo("جاري إنشاء كشف حساب PDF للعميل...");
    }

    private void formatCurrencyColumn(TableColumn<CustomerStatementRow, Double> col) {
        col.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else {
                    setText(String.format("%,.2f", item));
                    // تلوين المبالغ المدينة باللون الأحمر والدائنة بالأخضر لسهولة القراءة
                    if (col == colDebit && item > 0) setStyle("-fx-text-fill: #e74c3c;");
                    else if (col == colCredit && item > 0) setStyle("-fx-text-fill: #27ae60;");
                    else setStyle("-fx-text-fill: black;");
                }
            }
        });
    }
}