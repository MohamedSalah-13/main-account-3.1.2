package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Items_Package;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.table.TableSetting;
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

import static com.hamza.controlsfx.others.Utils.setTextFormatter;

@Log4j2
@FxmlPath(pathFile = "items/itemsPackage-view.fxml")
public class ItemsPackageController extends ServiceData {

    private final BooleanProperty itemHasPackage = new SimpleBooleanProperty();
    private final ListProperty<Items_Package> items_packageList = new SimpleListProperty<>();
    private ObjectProperty<ItemsModel> itemsModelObjectProperty;
    private StringProperty textSearchItems;
    @FXML
    private Button btnAddItem;
    @FXML
    private TableView<Items_Package> tableView;
    @FXML
    private VBox boxData;
    @FXML
    private CheckBox checkBox;
    @FXML
    private Label labelName, labelQuantity;
    @FXML
    private TextField txtCount;
    @FXML
    private GridPane gridPane;

    public ItemsPackageController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @FXML
    public void initialize() {
        nameSetting();
        other_setting();
        getTable();
        action();
        addTextSearchItems();
    }

    public void selectData(int itemPackageId) {
        if (isItemHasPackage()) {
            try {
                var itemsPackageByPackageId = itemPackageService.getItemsPackageByPackageId(itemPackageId);
                if (itemsPackageByPackageId != null) {
                    tableView.getItems().setAll(itemsPackageByPackageId);
                }
            } catch (DaoException e) {
                log.error("Failed to get items package by package id", e);
                tableView.getItems().clear();
                AllAlerts.alertError(e.getMessage());
            }
        }
    }

    private void addTextSearchItems() {
        try {
            TextSearchApplication<ItemsModel> customersTextSearchApplication = new TextSearchApplication<>(new ItemsSearch(itemsService));
            itemsModelObjectProperty = customersTextSearchApplication.getTextSearchController().itemSearchPropertyProperty();
            textSearchItems = customersTextSearchApplication.getTextSearchController().textNameProperty();
            gridPane.add(customersTextSearchApplication.getPane(), 1, 0);
        } catch (IOException e) {
            log.error("Failed to load text search view", e);
        }
    }

    private void nameSetting() {
        labelName.setText(Setting_Language.WORD_NAME);
        labelQuantity.setText(Setting_Language.WORD_QUANTITY);
        btnAddItem.setText(Setting_Language.WORD_ADD);
        checkBox.setText(Setting_Language.ADD_PACKAGE);
    }

    private void other_setting() {
        setTextFormatter(txtCount);
        btnAddItem.disableProperty().bind(txtCount.textProperty().lessThanOrEqualTo("0.0"));
        boxData.disableProperty().bind(checkBox.selectedProperty().not());

        items_packageList.bind(tableView.itemsProperty());
        checkBox.selectedProperty().bindBidirectional(itemHasPackageProperty());

    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, Items_Package.class);
        tableView.setEditable(true);
        TableSetting.tableMenuSetting(getClass(), tableView);

        TableColumn<Items_Package, Integer> columnId = new TableColumn<>(Setting_Language.WORD_CODE);
        columnId.setCellValueFactory(f -> f.getValue().idProperty().asObject());
        tableView.getColumns().addFirst(columnId);

        TableColumn<Items_Package, String> columnActiveName = new TableColumn<>(Setting_Language.NAME_ITEM);
        columnActiveName.setCellValueFactory(f -> f.getValue().getItemsModel().nameItemProperty());
        tableView.getColumns().add(1, columnActiveName);
        tableView.getColumns().get(1).setMinWidth(200);

        new ColumnSetting().enableDoubleEditing(2, t -> {
            int row = t.getTablePosition().getRow();
            Items_Package item = t.getTableView().getItems().get(row);

            try {
                //TODO 11/10/2025 10:51 AM Mohamed: check this in realtime
                item.setQuantity(t.getNewValue());
            } catch (NumberFormatException e) {
                log.error("Failed to set quantity", e);
                t.getTableView().refresh(); // Revert to original value
            }
            t.getTableView().refresh();
        }, tableView);


        tableView.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                tableView.getItems().remove(i);
                tableView.refresh();
            }
        }));


    }

    private void action() {
        txtCount.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                btnAddItem.fire();
            }
        });

        btnAddItem.setOnAction(actionEvent -> {
            addItemsToList();
            txtCount.clear();
        });
    }

    private void addItemsToList() {
        try {
            var itemsModel = itemsModelObjectProperty.get();
            if (itemsModel != null) {
                if (itemsModel.isHasPackage()) throw new DaoException("لا يمكن إدخال هذا الصنف , صنف له مجموعة");
                var e = new Items_Package(0, itemsModel.getId(), Double.parseDouble(txtCount.getText()));
                e.setItemsModel(itemsModel);
                tableView.getItems().add(e);
                textSearchItems.setValue(null);
            }
        } catch (Exception e) {
            log.error("Failed to add items package", e);
            AllAlerts.alertError(e.getMessage());
        }
    }

    public void deleteAllData() {
        tableView.getItems().clear();
        checkBox.setSelected(false);
    }

    public boolean isItemHasPackage() {
        return itemHasPackage.get();
    }

    public void setItemHasPackage(boolean itemHasPackage) {
        this.itemHasPackage.set(itemHasPackage);
    }

    public BooleanProperty itemHasPackageProperty() {
        return itemHasPackage;
    }

    public ObservableList<Items_Package> getItems_packageList() {
        return items_packageList.get();
    }

    public void setItems_packageList(ObservableList<Items_Package> items_packageList) {
        this.items_packageList.set(items_packageList);
    }

    public ListProperty<Items_Package> items_packageListProperty() {
        return items_packageList;
    }
}
