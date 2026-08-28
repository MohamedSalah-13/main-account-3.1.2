package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.service.StockService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.table.Columns;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

@FxmlPath(pathFile = "items/stocks-view.fxml")
public class StocksController {

    private final StockService service = ServiceRegistry.get(StockService.class);

    @FXML
    private TableView<Stock> table;
    @FXML
    private TextField name, address;

    @FXML
    public void initialize() {
        table.getColumns().setAll(
                Columns.text("stocks.name", Stock::getName),
                Columns.text("stocks.address", Stock::getAddress));
        TableSetting.tableMenuSetting(getClass(), table);
        refresh();
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldStock, newStock) -> {
            if (newStock != null) {
                name.setText(newStock.getName());
                address.setText(newStock.getAddress());
            }
        });
    }

    @FXML
    private void save() {
        try {
            Stock stock = table.getSelectionModel().getSelectedItem();
            if (stock == null) stock = new Stock(0, "", "");
            stock.setName(name.getText().trim());
            stock.setAddress(address.getText().trim());
            service.save(stock);
            AllAlerts.alertSave();
            clear();
            refresh();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("stocks.title"), e);
        }
    }

    @FXML
    private void remove() {
        try {
            Stock stock = table.getSelectionModel().getSelectedItem();
            if (stock == null) {
                AllAlerts.alertError(LanguageManager.getInstance().getString("msg.select.row"));
                return;
            }
            service.delete(stock.getId());
            AllAlerts.alertDelete();
            clear();
            refresh();
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("stocks.title"), e);
        }
    }

    @FXML
    private void clear() {
        table.getSelectionModel().clearSelection();
        name.clear();
        address.clear();
    }

    private void refresh() {
        try {
            table.setItems(FXCollections.observableArrayList(service.getStocks()));
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("stocks.title"), e);
        }
    }
}
