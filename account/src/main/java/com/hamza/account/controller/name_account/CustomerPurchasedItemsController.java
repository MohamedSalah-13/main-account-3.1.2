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
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
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

    private final ObservableList<PurchasedItemByCustomerView> data = FXCollections.observableArrayList();

    public CustomerPurchasedItemsController(DaoFactory daoFactory, int customerId) {
        this.purchasedItemsService = new CustomerPurchasedItemsService(daoFactory);
        this.customerService = new CustomerService(daoFactory);
        this.customerId = customerId;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        loadCustomerName();
        loadData();
    }

    private void setupTable() {
        new TableColumnAnnotation().getTable(tableView, PurchasedItemByCustomerView.class);
        tableView.setItems(data);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
            data.setAll(purchasedItemsService.getPurchasedItemsByCustomerId(customerId));
            tableView.setItems(data);
            if (labelCount != null) {
                labelCount.setText(String.valueOf(data.size()));
            }
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
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