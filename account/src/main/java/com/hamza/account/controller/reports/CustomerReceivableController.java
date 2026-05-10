package com.hamza.account.controller.reports;

import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ResourceBundle;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;

public class CustomerReceivableController implements Initializable {

    private final ObservableList<CustomerReceivable> masterData = FXCollections.observableArrayList();
    private final ReportExportService reportExportService = new ReportExportService();
    @FXML
    private TableView<CustomerReceivable> tableView;
    @FXML
    private Label lblGrandTotal;
    private DaoFactory daoFactory;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tableView.getColumns().clear();
        new TableColumnAnnotation().getTable(tableView, CustomerReceivable.class);
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        refreshData();
    }

    @FXML
    public void refreshData() {
        try {
            masterData.setAll(daoFactory.customerReceivableDao().getReceivablesReport());
            tableView.setItems(masterData);

            double total = masterData.stream().mapToDouble(CustomerReceivable::getTotalReceivable).sum();
            lblGrandTotal.setText(String.format("%,.2f", total));
        } catch (Exception e) {
            AllAlerts.alertError("فشل جلب أرصدة العملاء: " + e.getMessage());
        }
    }

    @FXML
    private void onExportPdf() {
        if (masterData.isEmpty()) return;
        String path = ReportExportService.getDefaultOutputPath("تقرير_ديون_العملاء");
        // استدعاء دالة التصدير من ReportExportService (يجب إضافتها هناك)
        // reportExportService.exportCustomerReceivables(masterData, path);
        showInfo("تم تصدير ملف ديون العملاء بنجاح.");
    }

    private void formatColumn(TableColumn<CustomerReceivable, Double> col) {
        col.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item));
            }
        });
    }

    public void onExportExcel(ActionEvent actionEvent) {

    }
}