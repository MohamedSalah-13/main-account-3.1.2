package com.hamza.account.controller.items;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import com.hamza.controlsfx.table.TreeTable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class ItemsControllerTree extends LoadData {

    private final ObservableList<ItemsModel> itemsModelObservableList;
    private final TreeTableView<ItemsModel> treeTableView = new TreeTableView<>();
    private final TextField txtSearch = new TextField();
    @Getter
    private final StackPane stackPane = new StackPane();
    private final MaskerPaneSetting maskerPaneSetting = new MaskerPaneSetting(stackPane);

    public ItemsControllerTree(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory, dataPublisher);
        this.itemsModelObservableList = FXCollections.observableArrayList();
        maskerPaneSetting.showMaskerPane(this::refreshData);
        table_data();
    }


    private void table_data() {
        stackPane.getChildren().add(treeTableView);
        treeTableView.getColumns().clear();
        TreeTable.createTable(treeTableView, initializeAccountColumnDefinitions());

        updateTreeStructure(false, null);

//        treeTableView.setOnDragDetected(event -> {
//            if (event.getSource() instanceof TreeTableColumn<?, ?> column) {
//                String columnId = column.getId();
//                updateTreeStructure(true, columnId);
//            }
//        });


        treeTableView.refresh();
        TableSetting.tableMenuSetting(getClass(), treeTableView);

        TreeTableColumn<ItemsModel, String> treeTableColumn = new TreeTableColumn<>("subGroups");
        treeTableColumn.setCellValueFactory(cellDataFeatures -> {
            ItemsModel item = cellDataFeatures.getValue().getValue();
            // Add null safety checks
            if (item != null && item.getSubGroups() != null) {
                return item.getSubGroups().nameProperty();
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });
        treeTableView.getColumns().add(treeTableColumn);

        TreeTableColumn<ItemsModel, String> treeTableColumnMain = new TreeTableColumn<>("mainGroups");
        treeTableColumn.setCellValueFactory(cellDataFeatures -> {
            ItemsModel item = cellDataFeatures.getValue().getValue();
            // Add null safety checks
            if (item != null && item.getSubGroups().getMainGroups() != null) {
                return item.getSubGroups().getMainGroups().nameProperty();
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });
        treeTableView.getColumns().add(treeTableColumnMain);

        // Add context menu to each column
        for (TreeTableColumn<ItemsModel, ?> column : treeTableView.getColumns()) {
            ContextMenu contextMenu = new ContextMenu();

            MenuItem groupByItem = new MenuItem("Group by " + column.getText());
            groupByItem.setOnAction(event -> {
                String columnId = column.getText();
                if (columnId != null && !columnId.isEmpty()) {
                    updateTreeStructure(true, columnId);
                }
            });

            MenuItem ungroupItem = new MenuItem("Clear grouping");
            ungroupItem.setOnAction(event -> updateTreeStructure(false, null));

            contextMenu.getItems().addAll(groupByItem, ungroupItem);
            column.setContextMenu(contextMenu);
        }
    }


//    private void updateTreeStructure(boolean groupBy, String columnId) {
//        TreeItem<ItemsModel> root = new TreeItem<>();
//        System.out.println(columnId);
//        if (groupBy) {
//            var groupedItems = itemsModelObservableList.stream()
//                    .collect(Collectors.groupingBy(item -> {
//                        if ("nameItem".equals(columnId)) return item.getNameItem();
//                        if ("barcode".equals(columnId)) return item.getBarcode();
//                        if ("buyPrice".equals(columnId)) return item.getBuyPrice();
//                        if ("subGroups".equals(columnId)) return item.getSubGroups().getName();
//                        return item.getSubGroups().getName();
//                    }));
//
//            groupedItems.forEach((groupName, items) -> {
//                TreeItem<ItemsModel> groupNode = new TreeItem<>(new ItemsModel(1, String.valueOf(groupName)));
//                items.forEach(item -> groupNode.getChildren().add(new TreeItem<>(item)));
//                root.getChildren().add(groupNode);
//            });
//        } else {
//            // Flat structure
//            itemsModelObservableList.forEach(item ->
//                    root.getChildren().add(new TreeItem<>(item)));
//        }
//
//        treeTableView.setRoot(root);
//    }

    private void updateTreeStructure(boolean groupBy, String columnId) {
        TreeItem<ItemsModel> root = new TreeItem<>(new ItemsModel()); // Create with empty model instead of null
        root.setExpanded(true);
        System.out.println(columnId);
        if (groupBy) {
            var groupedItems = itemsModelObservableList.stream()
                    .collect(Collectors.groupingBy(item -> {
                        if ("nameItem".equals(columnId)) return item.getNameItem();
                        if ("barcode".equals(columnId)) return item.getBarcode();
                        if ("buyPrice".equals(columnId)) return String.valueOf(item.getBuyPrice());
                        if ("subGroups".equals(columnId)) {
                            return item.getSubGroups() != null ? item.getSubGroups().getName() : "Unknown";
                        }
                        if ("mainGroups".equals(columnId)) {
                            return item.getSubGroups().getMainGroups() != null ? item.getSubGroups().getMainGroups().getName() : "Unknown";
                        }
                        return item.getSubGroups() != null ? item.getSubGroups().getName() : "Unknown";
                    }));

            groupedItems.forEach((groupName, items) -> {
                // Create a proper ItemsModel for group headers with null-safe initialization
                ItemsModel groupModel = new ItemsModel();
                groupModel.setId(0);
                groupModel.setNameItem(String.valueOf(groupName));
                TreeItem<ItemsModel> groupNode = new TreeItem<>(groupModel);
                groupNode.setExpanded(true);
                items.forEach(item -> groupNode.getChildren().add(new TreeItem<>(item)));
                root.getChildren().add(groupNode);
            });
        } else {
            // Flat structure
            itemsModelObservableList.forEach(item ->
                    root.getChildren().add(new TreeItem<>(item)));
        }

        treeTableView.setRoot(root);
        treeTableView.setShowRoot(false); // Hide the root item
    }

    private void refreshData() {
        var itemsModelList = itemsService.getMainItemsList();
        itemsModelObservableList.setAll(itemsModelList);
    }

    private List<Column<?>> initializeAccountColumnDefinitions() {
        return new ArrayList<>(Arrays.asList(
                new Column<>(Integer.class, "id", Setting_Language.WORD_NUM),
                new Column<>(String.class, "barcode", "barcode"),
                new Column<>(String.class, "nameItem", "nameItem"),
                new Column<>(Double.class, "buyPrice", "buyPrice")
        ));
    }
}
