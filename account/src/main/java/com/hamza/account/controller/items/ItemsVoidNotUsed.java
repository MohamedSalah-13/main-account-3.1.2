package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.domain.*;
import com.hamza.account.service.*;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.table.AddColumnMix;
import com.hamza.controlsfx.table.ColumnInterface;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hamza.controlsfx.dateTime.DateUtils.generateRandomBarcode;

@Log4j2
public record ItemsVoidNotUsed(ObservableList<ItemsModel> itemsModelObservableList, TableView<ItemsModel> tableView,
                               ServiceData serviceData, Publisher<ItemsModel> publisherAddItem) {

    public void applyRowColoringForBalance() {
        tableView.setRowFactory(itemsModelTableView -> {
            TableRow<ItemsModel> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    if (newValue.getSumAllBalance() == newValue.getMini_quantity()) {
                        row.setStyle("-fx-background-color: rgba(243,253,163,0.62)");
                    } else if (newValue.getSumAllBalance() <= 0) {
                        row.setStyle("-fx-background-color: #ff000033");
                    } else {
                        row.setStyle("-fx-background-color: #ffffff");
                    }
                }
            });
            return row;
        });

    }

    public void createActiveColumn() {
        TableColumn<ItemsModel, Boolean> activeColumn = new TableColumn<>("Active");
        activeColumn.setCellValueFactory(cellData -> cellData.getValue().activeItemProperty());
        activeColumn.setCellFactory(column -> new CheckBoxTableCell<ItemsModel, Boolean>(index -> {
            ItemsModel item = tableView.getItems().get(index);

            // Add listener to the property to detect changes
            item.activeItemProperty().addListener((obs, oldValue, newValue) -> {
                //                    updateItemAndRefresh(item, tableView);
            });

            return item.activeItemProperty();
        }));

        tableView.getColumns().add(activeColumn);
    }

    public void createActiveColumn2() {
        TableColumn<ItemsModel, Boolean> activeColumn = new TableColumn<>("Active");
        activeColumn.setCellValueFactory(cellData -> cellData.getValue().activeItemProperty());

        activeColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private ItemsModel currentItem;

            {
                checkBox.setOnAction(event -> {
                    if (currentItem != null) {
                        boolean newValue = checkBox.isSelected();
                        boolean oldValue = currentItem.isActiveItem();

                        currentItem.setActiveItem(newValue);
//                            updateItemAndRefresh(currentItem, tableView);
                    }
                });
            }

            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);

                if (empty || getTableRow() == null) {
                    setGraphic(null);
                    currentItem = null;
                } else {
                    currentItem = getTableRow().getItem();
                    checkBox.setSelected(value != null && value);
                    setGraphic(checkBox);
                }
            }
        });

        tableView.getColumns().add(activeColumn);
    }

    public void duplicateSelectedRow() {
        ItemsService itemsService = serviceData.getItemsService();
        try {
            if (tableView.getSelectionModel().isEmpty()) {
                throw new Exception(Setting_Language.PLEASE_SELECT_ROW);
            }

            String randomItemBarcode = generateRandomBarcode(itemsService.getMaxItemId() + 1);
            ItemsModel selectedItem = tableView.getSelectionModel().getSelectedItem();
            ItemsModel duplicatedItem = new ItemsModel();

            duplicatedItem.setNameItem(selectedItem.getNameItem() + " (Copy)");
//            duplicatedItem.setBarcode(selectedItem.getBarcode() + "_copy");
            duplicatedItem.setBarcode(randomItemBarcode);
            duplicatedItem.setBuyPrice(selectedItem.getBuyPrice());
            duplicatedItem.setSelPrice1(selectedItem.getSelPrice1());
            duplicatedItem.setMini_quantity(selectedItem.getMini_quantity());
            duplicatedItem.setFirstBalanceForStock(selectedItem.getFirstBalanceForStock());
            duplicatedItem.setSubGroups(selectedItem.getSubGroups());
            duplicatedItem.setUnitsType(new UnitsModel(1));

            var i = itemsService.updateItem(duplicatedItem);
//            publisherAddItem.notifyObservers();
            if (i >= 1) {
                AllAlerts.alertSaveWithMessage("Item duplicated successfully");
                publisherAddItem.setAvailability(duplicatedItem);
            }

        } catch (Exception e) {
            logErrors(e);
        }
    }

    public void addColumnsGroups(TableView<ItemsModel> tableView) {
        Callback<TableColumn.CellDataFeatures<ItemsModel, String>, ObservableValue<String>> main = tf -> tf.getValue().getSubGroups().getMainGroups().nameProperty();
        Callback<TableColumn.CellDataFeatures<ItemsModel, String>, ObservableValue<String>> sub = tf -> tf.getValue().getSubGroups().nameProperty();

        var columnInterface = new ColumnInterface<ItemsModel, String>() {
            @Override
            public HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, String>, ObservableValue<String>>> STRING_CALLBACK_HASH_MAP() {
                HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, String>, ObservableValue<String>>> hashMap = new HashMap<>();
                hashMap.put(Setting_Language.MAIN, main);
                hashMap.put(Setting_Language.SUB, sub);
                return hashMap;
            }
        };
        tableView.getColumns().add(new AddColumnMix<ItemsModel, String>().getTableColumn(Setting_Language.WORD_GROUP, columnInterface));
    }

    public List<ItemsModel> getAllItemsNotUsed() throws DaoException {
        SalesService salesService = serviceData.getSalesService();
        SalesReService salesReService = serviceData.getSalesReService();
        PurchaseService purchaseService = serviceData.getPurchaseService();
        PurchaseReService purchaseReService = serviceData.getPurchaseReService();

        Set<Integer> numList = new HashSet<>();
        log.info("list size: {}", itemsModelObservableList.size());
        var items = itemsModelObservableList;
        for (ItemsModel item : items) {
            var listSales = salesService.findByNumItem(item.getId()).stream().map(Sales::getNumItem).toList();
            var listSalesRe = salesReService.findByNumItem(item.getId()).stream().map(Sales_Return::getNumItem).toList();
            var listPurchase = purchaseService.findByNumItem(item.getId()).stream().map(Purchase::getNumItem).toList();
            var listPurchaseRe = purchaseReService.findByNumItem(item.getId()).stream().map(Purchase_Return::getNumItem).toList();

            numList.addAll(listSales);
            numList.addAll(listSalesRe);
            numList.addAll(listPurchase);
            numList.addAll(listPurchaseRe);

        }
        log.info("numList: {}", numList.size());
        return items.stream().filter(item -> !numList.contains(item.getId())).toList();
    }

    private void logErrors(Exception e) {
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
    }
}
