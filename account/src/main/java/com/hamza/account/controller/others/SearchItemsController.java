package com.hamza.account.controller.others;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.AppIcon;
import com.hamza.account.config.UiScale;
import com.hamza.account.controller.items.ColumnImage;
import com.hamza.account.controller.items.PaginationTableSetting;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.DesignInterface;
import com.hamza.account.document.InvoiceBuy;
import com.hamza.account.features.invoice.InvoiceLineService;
import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.ButtonDeleteRow;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.service.ItemsService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.ImageDesign;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Columns;
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
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

import static com.hamza.account.config.PropertiesName.getSearchItemsSplitPaneDivider;
import static com.hamza.account.config.PropertiesName.setSearchItemsSplitPaneDivider;

@FxmlPath(pathFile = "search-view.fxml")
public class SearchItemsController implements Initializable {

    private final DesignInterface designInterface;
    private final DataInterface<?, ?, ?, ?> dataInterface;
    private final InvoiceBuy<?, ?, ?, ?> invoiceBuy;
    private final ObservableList<ItemsModel> itemsModels = FXCollections.observableArrayList();
    private final ListProperty<BasePurchasesAndSales> selectedItem = new SimpleListProperty<>();
    private final TableView<ItemsModel> tableItems = new TableView<>();
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    @Getter
    @FXML
    private TableView<BasePurchasesAndSales> tableView;
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
    @FXML
    private FontIcon headerIcon, searchIcon;
    @FXML
    private Label labelTitle, labelSubtitle, labelCount;

    public SearchItemsController(DataInterface<?, ?, ?, ?> dataInterface) {
        this.dataInterface = dataInterface;
        this.invoiceBuy = dataInterface.invoiceBuy();
        this.designInterface = dataInterface.designInterface();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        headerSetting();
        otherSetting();
        getTableItems();
        createTablePurchase();
        buttonSetting();
        new PaginationTableSetting(tableItems, itemsService
                , txtSearch, pagination).initializePagination();
    }

    private void headerSetting() {
        var lm = LanguageManager.getInstance();
        int size = (int) Math.round(20 * UiScale.factor());

        headerIcon.setIconCode(Feather.SEARCH);
        headerIcon.setIconSize(size);
        searchIcon.setIconCode(Feather.SEARCH);
        searchIcon.setIconSize(size);

        labelTitle.setText(Setting_Language.WORD_SEARCH);
        labelSubtitle.setText(lm.getString("search.items.subtitle"));

        tableView.getItems().addListener((javafx.collections.ListChangeListener<BasePurchasesAndSales>) change -> updateAddedCount());
        updateAddedCount();
    }

    private void updateAddedCount() {
        labelCount.setText(String.format(LanguageManager.getInstance().getString("search.items.added.count"),
                tableView.getItems().size()));
    }

    private void buttonSetting() {
        btnSave.setText(Setting_Language.OK);
        btnSave.setGraphic(AppIcon.CONFIRM.graphic());
        btnClose.setText(Setting_Language.WORD_CANCEL);
        btnClose.setGraphic(AppIcon.CLOSE.graphic());
        btnClose.setId("btnClose");
        btnAdd2.setGraphic(AppIcon.ADD.graphic());
    }

    private void otherSetting() {
        btnAdd2.setText(Setting_Language.WORD_ADD);
        labelSearch.setText(Setting_Language.WORD_SEARCH);
        txtSearch.setPromptText(Setting_Language.WORD_SEARCH);

        btnAdd2.setOnAction(event -> {
            try {
                addItemsInOtherTable().action(tableItems.getSelectionModel().getSelectedIndex());
            } catch (Exception e) {
                AllAlerts.handleError("اختيار الصنف", e);
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

        splitPane.setDividerPosition(0, getSearchItemsSplitPaneDivider());
        splitPane.getDividers().getFirst().positionProperty().addListener(
                (obs, oldPos, newPos) ->
                        setSearchItemsSplitPaneDivider(splitPane.getDividers().getFirst().getPosition()));

        btnSave.setOnAction(event -> {
            setSelectedItem(tableView.getItems());
            btnClose.fire();
        });

        btnClose.setOnAction(actionEvent -> btnClose.getScene().getWindow().hide());
    }

    private void getTableItems() {
        tableItems.getColumns().addAll(
                Columns.number(NamesTables.CODE, ItemsModel::getId),
                Columns.text(NamesTables.STRING, ItemsModel::getBarcode),
                Columns.text(NamesTables.NAME_ITEM, ItemsModel::getNameItem),
                Columns.number(NamesTables.BUY_PRICE, ItemsModel::getBuyPrice),
                Columns.number(NamesTables.SEL_PRICE, ItemsModel::getSelPrice1),
                Columns.number(NamesTables.SEL_PRICE + "2", ItemsModel::getSelPrice2),
                Columns.number(NamesTables.SEL_PRICE + "3", ItemsModel::getSelPrice3),
                Columns.number(NamesTables.MINI_QUANTITY, ItemsModel::getMini_quantity),
                Columns.number(NamesTables.FIRST_BALANCE, ItemsModel::getFirstBalanceForStock),
                Columns.number(NamesTables.SUM_ALL_BALANCE, ItemsModel::getSumAllBalance)
        );
        tableItems.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableItems.setItems(itemsModels);
        tableItems.getColumns().get(3).setVisible(!designInterface.showDataForCustomer());
        tableItems.getColumns().get(4).setVisible(designInterface.showDataForCustomer());
        new ColumnImage(tableItems, itemsService).addColumnImage();
        TableSetting.tableMenuSetting(getClass(), tableItems);

    }


    private void createTablePurchase() {
        tableView.getColumns().addAll(
                Columns.number(NamesTables.QUANTITY, BasePurchasesAndSales::getQuantity),
                Columns.number(NamesTables.PRICE, BasePurchasesAndSales::getPrice),
                Columns.number(NamesTables.TOTAL, BasePurchasesAndSales::getTotal),
                Columns.number(NamesTables.DISCOUNT, BasePurchasesAndSales::getDiscount),
                Columns.number(NamesTables.TOTAL_AFTER, BasePurchasesAndSales::getTotal_after_discount)
        );

        TableColumn<BasePurchasesAndSales, ?> tableColumnSelPriceType = addColumn(Setting_Language.NAME_ITEM
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
            InvoiceLineService.recalculate(purchase);
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
        List<BasePurchasesAndSales> list = new ArrayList<>();
        Optional<BasePurchasesAndSales> first = tableView.getItems().stream()
                .filter(t1 -> t1.getItems().equals(itemsModel)).findFirst();
        if (first.isEmpty()) {
            // The row starts in the item's base unit, whose factor is 1 - not in
            // whatever units.value_d happens to say about that unit.
            list.add(invoiceBuy.object_TableData(0, 0, itemsModel.getId(), price, 1, 0, price, ItemUnits.baseUnit(itemsModel), itemsModel, null));
            tableView.getItems().addAll(list);
        }
    }

    public ObservableList getSelectedItem() {
        return selectedItem.get();
    }

    public void setSelectedItem(ObservableList selectedItem) {
        this.selectedItem.set(selectedItem);
    }

    public ListProperty<BasePurchasesAndSales> selectedItemProperty() {
        return selectedItem;
    }
}
