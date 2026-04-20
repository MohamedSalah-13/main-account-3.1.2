package com.hamza.account.controller.name_account;

import com.hamza.account.controller.model.PurchasedItemByCustomerView;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.CustomerPurchasedItemsService;
import com.hamza.account.service.CustomerService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

@Log4j2
@FxmlPath(pathFile = "customer-purchased-items-view.fxml")
public class CustomerPurchasedItemsController implements Initializable, AppSettingInterface {

    private final CustomerPurchasedItemsService purchasedItemsService;
    private final CustomerService customerService;
    private final int customerId;

    @FXML
    private TableView<PurchasedItemByCustomerView> tableView;
    @FXML
    private Label labelCustomerName;
    @FXML
    private Label labelCount;
    @FXML
    private Label labelTotalSales;
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

    private final ObservableList<PurchasedItemByCustomerView> masterData = FXCollections.observableArrayList();
    private final ObservableList<PurchasedItemByCustomerView> filteredData = FXCollections.observableArrayList();

    public CustomerPurchasedItemsController(DaoFactory daoFactory, int customerId) {
        this.purchasedItemsService = new CustomerPurchasedItemsService(daoFactory);
        this.customerService = new CustomerService(daoFactory);
        this.customerId = customerId;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupControls();
        loadCustomerName();
        loadData();
        applyFilters();
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, PurchasedItemByCustomerView.class);
        tableView.setItems(filteredData);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupControls() {
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);

        if (btnSearch != null) {
            btnSearch.setOnAction(e -> applyFilters());
        }
        if (btnReset != null) {
            btnReset.setOnAction(e -> {
                if (dateFrom != null) dateFrom.setValue(null);
                if (dateTo != null) dateTo.setValue(null);
                if (textSearchName != null) textSearchName.clear();
                applyFilters();
            });
        }
    }

    private void loadCustomerName() {
        try {
            Customers customer = customerService.getCustomerById(customerId);
            if (customer != null && labelCustomerName != null) {
                labelCustomerName.setText(customer.getName());
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }
    }

    private void loadData() {
        try {
            masterData.setAll(purchasedItemsService.getPurchasedItemsByCustomerId(customerId));
            applyFilters();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }
    }

    private void applyFilters() {
        var result = masterData.stream().toList();

        if (dateFrom != null && dateTo != null && dateFrom.getValue() != null && dateTo.getValue() != null) {
            LocalDate from = dateFrom.getValue();
            LocalDate to = dateTo.getValue();
            if (from.isAfter(to)) {
                AllAlerts.alertError("تاريخ البداية يجب أن يكون قبل تاريخ النهاية");
                return;
            }
            result = purchasedItemsService.filterByDateRange(result, from, to);
        }

        if (textSearchName != null && !textSearchName.getText().isBlank()) {
            result = purchasedItemsService.filterByItemName(result, textSearchName.getText());
        }

        filteredData.setAll(result);
        tableView.refresh();

        if (labelCount != null) {
            labelCount.setText(String.valueOf(filteredData.size()));
        }
        if (labelTotalSales != null) {
            labelTotalSales.setText(String.valueOf(purchasedItemsService.sumTotalSales(filteredData)));
        }
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new Pane();
    }

    @Override
    public String title() {
        return "الأصناف المشتراة من العميل";
    }

    @Override
    public boolean resize() {
        return true;
    }
}