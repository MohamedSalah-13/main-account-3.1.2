package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.items.ColumnImage;
import com.hamza.account.controller.items.PaginationTableSetting;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.interfaces.api.InvoiceBuy;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getPosSplitPaneDividerSearchItems;
import static com.hamza.account.config.PropertiesName.setPosSplitPaneDividerSearchItems;
import static com.hamza.account.controller.invoice.UpdateInvoiceRow.updateData;
import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "search-view.fxml")
public class SearchItemsController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        implements Initializable {

    private final DesignInterface designInterface;
    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private final InvoiceBuy<T1, T2, T3, T4> invoiceBuy;
    private final ObservableList<ItemsModel> itemsModels = FXCollections.observableArrayList();
    private final ListProperty<T1> selectedItem = new SimpleListProperty<>();
    private final TableView<ItemsModel> tableItems = new TableView<>();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    @Getter
    @FXML
    private TableView<T1> tableView;
    @FXML
    private TextField txtSearch;
    @FXML
    private Button btnAdd2;
    @FXML
    private Label labelSearch;
    @FXML
    private StackPane stackPane;
    @FXML
    private CheckBox checkAddDirect;
    @FXML
    private SplitPane splitPane;
    @FXML
    private Button btnClose, btnSave;
    @FXML
    private Pagination pagination;

    public SearchItemsController(DataInterface<T1, T2, T3, T4> dataInterface) {
        this.dataInterface = dataInterface;
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.designInterface = dataInterface.designInterface();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        otherSetting();
        getTableItems();
        createTablePurchase();
        buttonSetting();
        new PaginationTableSetting(tableItems, itemsService
                , txtSearch, pagination).initializePagination();
    }


    private void buttonSetting() {
        var images = new Image_Setting();
        btnSave.setText(Setting_Language.OK);
        btnSave.setGraphic(createIcon(images.save));
        btnClose.setText(Setting_Language.WORD_CANCEL);
        btnClose.setGraphic(createIcon(images.cancel));
        btnClose.setId("btnClose");
    }

    private void otherSetting() {
        btnAdd2.setText(Setting_Language.WORD_ADD);
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);

        btnAdd2.setOnAction(event -> {
            try {
                addItemsInOtherTable().action(tableItems.getSelectionModel().getSelectedIndex());
            } catch (Exception e) {
                log.error(this.getClass().getName(), e.getMessage());
                AllAlerts.showExceptionDialog(e);
            }
        });

        txtSearch.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DOWN) {
                tableItems.getSelectionModel().selectFirst();
                tableItems.requestFocus();
            }
        });

        tableItems.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) btnAdd2.fire();
        });
        tableItems.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                btnAdd2.fire();
            }
        });

        splitPane.setDividerPosition(0, getPosSplitPaneDividerSearchItems());
        splitPane.getDividers().getFirst().positionProperty().addListener(
                (obs, oldPos, newPos) ->
                        setPosSplitPaneDividerSearchItems(splitPane.getDividers().getFirst().getPosition()));

        btnSave.setOnAction(event -> {
            setSelectedItem(tableView.getItems());
            btnClose.fire();
        });

        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
    }

    private void getTableItems() {
        new TableColumnAnnotation().getTable(tableItems, ItemsModel.class);
        tableItems.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableItems.setItems(itemsModels);
        tableItems.getColumns().get(3).setVisible(!designInterface.showDataForCustomer());
        tableItems.getColumns().get(4).setVisible(designInterface.showDataForCustomer());
        new ColumnImage(tableItems, itemsService).addColumnImage();
        TableSetting.tableMenuSetting(getClass(), tableItems);

    }


    private void createTablePurchase() {
        new TableColumnAnnotation().getTable(tableView, BasePurchasesAndSales.class);

        TableColumn<T1, ?> tableColumnSelPriceType = addColumn(Setting_Language.NAME_ITEM
                , f -> f.getValue().getItems().nameItemProperty());
        tableView.getColumns().addFirst(tableColumnSelPriceType);
//        tableView.getColumns().getFirst().setPrefWidth(250);

        tableView.getColumns().add(new ButtonColumn<>(new ButtonDeleteRow() {
            @Override
            public void action(int i) {
                tableView.getItems().remove(i);
            }
        }));

        tableView.setEditable(true);
        editeTablePurchase();
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void editeTablePurchase() {
        new ColumnSetting().enableDoubleEditing(1, t -> {
            int row = t.getTablePosition().getRow();
            BasePurchasesAndSales purchase = t.getTableView().getItems().get(row);
            purchase.setQuantity(t.getNewValue() == null ? 1.0 : t.getNewValue());
            updateData(purchase);
        }, tableView);
    }

    private <T> TableColumn<T, String> addColumn(String name, Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>> cellData) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cellData);
        return column;
    }


    private ButtonColumnI addItemsInOtherTable() {
        return new ButtonColumnI() {
            @Override
            public void action(int index) {
                if (!tableItems.getSelectionModel().isEmpty()) {
                    List<ItemsModel> selectedItems = tableItems.getSelectionModel().getSelectedItems();
                    if (selectedItems.size() > 1) {
                        handleMultipleSelections(selectedItems);
                    } else {
                        handleSingleSelection(index);
                    }
                }
            }

            @NotNull
            @Override
            public String columnTitle() {
                return "";
            }

            @NotNull
            @Override
            public String textName() {
                return Setting_Language.WORD_ADD;
            }

            @Override
            public Node imageNode() {
                return new ImageDesign(new Image_Setting().addBlack24);
            }
        };
    }

    private void handleMultipleSelections(List<ItemsModel> selectedItems) {
        for (ItemsModel item : selectedItems) {
            processItemsData(item);
        }
    }

    private void handleSingleSelection(int index) {
        ItemsModel itemsModel = tableItems.getItems().get(index);
        processItemsData(itemsModel);
    }

    private void processItemsData(ItemsModel itemsModel) {
        double price = itemsModel.getBuyPrice();
        if (dataInterface.designInterface().showDataForCustomer()) {
            price = itemsModel.getSelPrice1();
        }
        addData(itemsModel, price);
    }

    private void addData(ItemsModel itemsModel, double price) {
        List<T1> list = new ArrayList<>();
        Optional<T1> first = tableView.getItems().stream().filter(t1 -> t1.getItems().equals(itemsModel)).findFirst();
        if (first.isEmpty()) {
            list.add(invoiceBuy.object_TableData(0, 0, itemsModel.getId(), price, 1, 0, price, itemsModel.getUnitsType(), itemsModel, null));
            tableView.getItems().addAll(list);
        }
    }

    public ObservableList getSelectedItem() {
        return selectedItem.get();
    }

    public void setSelectedItem(ObservableList selectedItem) {
        this.selectedItem.set(selectedItem);
    }

    public ListProperty<T1> selectedItemProperty() {
        return selectedItem;
    }
}