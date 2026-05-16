package com.hamza.account.controller.convert_stock;

import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.List;

public class ShowDataListStock implements ShowDataTransferList<StockTransferListItems> {

    private final StockTransfer stockTransfer;

    public ShowDataListStock(StockTransfer stockTransfer) {
        this.stockTransfer = stockTransfer;
    }

    @Override
    public Class<StockTransferListItems> classOfColumns() {
        return StockTransferListItems.class;
    }

    @Override
    public List<StockTransferListItems> listTable() {
        if (stockTransfer.getTransferListItems() == null) {
            return List.of();
        }

        return stockTransfer.getTransferListItems();
    }

    @Override
    public String titlePane() {
        return "تفاصيل تحويل مخزني رقم "
                + stockTransfer.getId()
                + " - "
                + stockTransfer.getDate()
                + " - من "
                + stockTransfer.getStockFrom().getName()
                + " إلى "
                + stockTransfer.getStockTo().getName();
    }

    @Override
    public void tableData(TableView<StockTransferListItems> tableView) {
        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> itemName =
                cell -> cell.getValue().getItem().nameItemProperty();

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> barcode =
                cell -> new ReadOnlyStringWrapper(cell.getValue().getItem().getBarcode());

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> quantity =
                cell -> new ReadOnlyStringWrapper(formatNumber(cell.getValue().getQuantity()));

        ColumnSetting.addColumn(tableView, "اسم الصنف", 1, itemName);
        ColumnSetting.addColumn(tableView, "الباركود", 2, barcode);
        ColumnSetting.addColumn(tableView, "الكمية المحولة", 3, quantity);

        tableView.getColumns().forEach(column -> column.setMinWidth(120));

        if (tableView.getColumns().size() > 1) {
            tableView.getColumns().get(1).setPrefWidth(280);
        }
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        return String.format("%.3f", value);
    }
}