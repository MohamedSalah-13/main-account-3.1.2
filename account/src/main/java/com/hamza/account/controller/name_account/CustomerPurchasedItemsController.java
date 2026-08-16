package com.hamza.account.controller.name_account;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.interfaces.CustomerPurchaseInterface;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CustomerPurchasedItemsService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.excel.ExcelException;
import com.hamza.controlsfx.excel.ExportData;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

@FxmlPath(pathFile = "customer/customer-purchased-items-view.fxml")
public class CustomerPurchasedItemsController implements Initializable, AppSettingInterface {

    private final CustomerPurchasedItemsService purchasedItemsService;
    private final CustomerPurchaseInterface customerPurchaseInterface;

    private final int customerId;
    private final String customerName;
    private final ObservableList<CustomerPurchasedItem> masterData = FXCollections.observableArrayList();
    private final ObservableList<CustomerPurchasedItem> filteredData = FXCollections.observableArrayList();
    @FXML
    private TableView<CustomerPurchasedItem> tableView;
    @FXML
    private Label labelCustomerName, title;
    @FXML
    private Label labelCount;
    @FXML
    private Label labelTotalSales;
    @FXML
    private Label labelTotalQuantity;
    @FXML
    private Label labelNetTotal;
    @FXML
    private DatePicker dateFrom;
    @FXML
    private DatePicker dateTo;
    @FXML
    private TextField textSearchName;
    @FXML
    private Button btnSearch;
    @FXML
    private Button btnReset;
    @FXML
    private Button btnExportExcel;
    @FXML
    private Button btnExportPdf;
    @FXML
    private Button btnSortDate;

    public CustomerPurchasedItemsController(DaoFactory daoFactory, int customerId, String customerName
            , CustomerPurchaseInterface customerPurchaseInterface) {
        this.customerPurchaseInterface = customerPurchaseInterface;
        this.purchasedItemsService = new CustomerPurchasedItemsService(daoFactory);
        this.customerId = customerId;
        this.customerName = customerName;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupControls();
        loadData();
//        applyFilters();
        title.setText(customerPurchaseInterface.title());
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, CustomerPurchasedItem.class);
        tableView.setItems(filteredData);
        TableSetting.tableMenuSetting(getClass(), tableView);
//        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupControls() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);

        if (btnSearch != null) {
            btnSearch.setOnAction(e -> applyFilters());
        }
        if (btnReset != null) {
            btnReset.setOnAction(e -> resetFilters());
        }
        if (btnSortDate != null) {
            btnSortDate.setOnAction(e -> {
                masterData.setAll(purchasedItemsService.sortByDateDescending(masterData));
                applyFilters();
            });
        }
        if (btnExportExcel != null) {
            btnExportExcel.setOnAction(e -> exportExcel());
        }
        if (btnExportPdf != null) {
            btnExportPdf.setOnAction(e -> exportPdf());
        }
    }


    private void loadData() {
        try {
            masterData.setAll(customerPurchaseInterface.getPurchasedItemsByCustomerId(customerId));
            labelCustomerName.setText(customerName);
        } catch (DaoException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("party.error.load.customer.purchases"), e);
        }
    }

    private void applyFilters() {
        var result = masterData.stream().toList();

        if (dateFrom != null && dateTo != null && dateFrom.getValue() != null && dateTo.getValue() != null) {
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            if (from.isAfter(to)) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.filter.customer.purchases"),
                        new UserValidationException(LanguageManager.getInstance().getString("party.error.date.range.invalid")));
                return;
            }
            result = purchasedItemsService.filterByDateRange(result, from, to);
        }

        if (textSearchName != null && !textSearchName.getText().isBlank()) {
            result = purchasedItemsService.filterByItemName(result, textSearchName.getText());
        }

        filteredData.setAll(result);
        tableView.refresh();
        updateSummary();
    }

    private void updateSummary() {
        if (labelCount != null) {
            labelCount.setText(String.valueOf(filteredData.size()));
        }
        if (labelTotalSales != null) {
            labelTotalSales.setText(String.valueOf(purchasedItemsService.sumTotalSales(filteredData)));
        }
        if (labelTotalQuantity != null) {
            labelTotalQuantity.setText(String.valueOf(purchasedItemsService.sumTotalQuantity(filteredData)));
        }
        if (labelNetTotal != null) {
            labelNetTotal.setText(String.valueOf(purchasedItemsService.sumTotalAfterDiscount(filteredData)));
        }
    }

    private void resetFilters() {
        if (dateFrom != null) dateFrom.setValue(null);
        if (dateTo != null) dateTo.setValue(null);
        if (textSearchName != null) textSearchName.clear();
        filteredData.setAll(masterData);
        updateSummary();
    }

    private void exportExcel() {
        try {
            if (filteredData.isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.export.customer.purchases"),
                        new UserValidationException(LanguageManager.getInstance().getString("party.error.no.data.export")));
                return;
            }
            int result = ExportData.exportDataToExcel(
                    filteredData.stream().toList(),
                    new CustomerPurchasedItemsExcelWriter(filteredData)
            );
            if (result >= 1) {
                AllAlerts.alertSaveWithMessage(LanguageManager.getInstance().getString("party.export.excel.success"));
            }
        } catch (ExcelException e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.export.customer.purchases"), e);
        }
    }

    private void exportPdf() {
        try {
            if (filteredData.isEmpty()) {
                AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.print.customer.purchases"),
                        new UserValidationException(LanguageManager.getInstance().getString("party.error.no.data.print")));
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle(LanguageManager.getInstance().getString("party.dialog.save.report"));
            fileChooser.setInitialFileName("items_" + labelCustomerName.getText() + ".pdf");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );

            File file = fileChooser.showSaveDialog(tableView.getScene().getWindow());
//            File file = new javafx.stage.FileChooser().showSaveDialog(tableView.getScene().getWindow());
            if (file == null) {
                return;
            }

            List<String[]> rows = filteredData.stream()
                    .map(row -> new String[]{
                            String.valueOf(row.getCustomerId()),
                            row.getCustomerName(),
                            row.getItemName(),
                            String.valueOf(row.getQuantity()),
                            String.valueOf(row.getSellingPrice()),
                            row.getInvoiceDate().toString(),
                            String.valueOf(row.getInvoiceNumber())
                    })
                    .toList();

            var lm = LanguageManager.getInstance();
            PdfExportService pdfExportService = new PdfExportService();
            boolean success = pdfExportService.exportGenericReport(
                    file.getAbsolutePath(),
                    customerPurchaseInterface.title(),
                    lm.getString("party.pdf.customer.label", labelCustomerName != null ? labelCustomerName.getText() : ""),
                    new String[]{lm.getString("party.column.customer.id"), lm.getString("name"), lm.getString("column.item_name")
                            , lm.getString("quantity"), lm.getString("price"), lm.getString("date"), lm.getString("column.code_invoice")},
                    new float[]{10, 14, 18, 10, 10, 10, 10},
                    rows,
                    lm.getString("total"),
                    String.valueOf(purchasedItemsService.sumTotalAfterDiscount(filteredData)),
                    null,
                    com.itextpdf.kernel.geom.PageSize.A4.rotate()
            );

            if (success) {
                AllAlerts.alertSaveWithMessage(lm.getString("party.export.pdf.success"));
            } else {
                AllAlerts.handleError(lm.getString("party.action.export.customer.purchases"),
                        new BusinessRuleException(lm.getString("party.error.export.generic")));
            }
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("party.action.print.customer.purchases"), e);
        }
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new Pane();
    }

    @Override
    public String title() {
        return customerPurchaseInterface.title();
    }

    @Override
    public boolean resize() {
        return true;
    }
}
