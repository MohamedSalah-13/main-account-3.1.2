package com.hamza.account.controller.reports;

import com.hamza.account.features.export.CustomerAccountData;
import com.hamza.account.features.export.ReportExportService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerReceivable;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExcelException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * The customer receivables report.
 * <p>
 * <b>Both export buttons were broken, and the PDF one lied.</b> {@code onExportPdf} had its
 * one working line commented out and then told the user the file had been saved;
 * {@code onExportExcel} had an empty body, so the button did nothing at all. Both write a
 * file now, and neither reports success unless one was written.
 * <p>
 * <b>And the column headings were English in an Arabic screen.</b>
 * {@link Columns#text(String, java.util.function.Function)} resolves its first argument
 * through {@link LanguageManager}, which answers a missing key with the key itself — so
 * {@code Columns.text("Customer Name", …)} rendered literally. The keys existed in all three
 * bundles the whole time, and the FXML's own {@code TableColumn}s already used them; the
 * controller cleared those columns and rebuilt them with the literals.
 * <p>
 * The figures come from {@code view_customer_receivables}, which is now derived from
 * {@code account_customer_totals} — the one definition of what a customer owes. It used to
 * compute its own, ignoring every sales return, so this report and the accounts screen
 * reported different debts for the same customer.
 */
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
        tableView.getColumns().addAll(
                Columns.text("report.customer.receivables.col.name", CustomerReceivable::getCustomerName),
                Columns.text("column.tel", CustomerReceivable::getCustomerPhone),
                Columns.number("report.customer.receivables.col.opening", CustomerReceivable::getOpeningBalance),
                Columns.number("report.customer.receivables.col.invoices", CustomerReceivable::getInvoicesDebt),
                Columns.number("report.customer.receivables.col.payments", CustomerReceivable::getTotalPayments),
                Columns.number("report.customer.receivables.col.total", CustomerReceivable::getTotalReceivable)
        );
        tableView.setPlaceholder(new Label(LanguageManager.getInstance().getString("party.error.no.data.export")));
        tableView.setItems(masterData);
    }

    public void setDaoFactory(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
        refreshData();
    }

    @FXML
    public void refreshData() {
        try {
            masterData.setAll(daoFactory.customerReceivableDao().getReceivablesReport());
            double total = masterData.stream().mapToDouble(CustomerReceivable::getTotalReceivable).sum();
            lblGrandTotal.setText(String.format("%,.2f", total));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("report.error.load.receivables.title"), e);
        }
    }

    @FXML
    private void onExportPdf() {
        try {
            requireRows();
            String path = ReportExportService.getDefaultOutputPath("Customer_Receivables_Report");
            List<CustomerAccountData> rows = masterData.stream()
                    .map(row -> CustomerAccountData.builder()
                            .customerName(row.getCustomerName())
                            .debit(row.getInvoicesDebt())
                            .credit(row.getTotalPayments())
                            .balance(row.getTotalReceivable())
                            .build())
                    .toList();
            if (!reportExportService.exportCustomerAccountsReport(rows, path)) {
                throw new BusinessRuleException(LanguageManager.getInstance()
                        .getString("party.error.export.generic"));
            }
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance()
                    .getString("report.customer.receivables.export.pdf.done", path));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("report.error.print.title"), e);
        }
    }

    @FXML
    private void onExportExcel() {
        try {
            requireRows();
            int written = ExportData.exportDataToExcel(masterData.stream().toList(),
                    new CustomerReceivableExcelWriter(masterData.stream().toList()));
            if (written < 1) {
                throw new ExcelException(LanguageManager.getInstance().getString("party.error.cannot.save"));
            }
            AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("party.export.excel.success"));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("report.error.print.title"), e);
        }
    }

    /**
     * Refuses an export of nothing, out loud.
     * <p>
     * Both buttons used to return silently on an empty table — and the PDF one then said it
     * had saved anyway. A user who clicks export and is told nothing assumes a file exists.
     */
    private void requireRows() throws UserValidationException {
        if (masterData.isEmpty()) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("party.error.no.data.export"));
        }
    }
}
