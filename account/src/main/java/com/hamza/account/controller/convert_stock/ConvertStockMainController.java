package com.hamza.account.controller.convert_stock;

import com.hamza.account.controller.others.AddStockController;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.StockTransfer;
import com.hamza.account.model.domain.StockTransferListItems;
import com.hamza.account.openFxml.AddForAllApplication;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.StockService;
import com.hamza.account.service.StockTransferService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import com.hamza.controlsfx.util.MaxNumberList;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.converter.DoubleStringConverter;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.SearchableComboBox;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Log4j2
@FxmlPath(pathFile = "convert_stocks.fxml")
public class ConvertStockMainController implements AppSettingInterface {

    private static final int ITEMS_SEARCH_LIMIT = 100;
    private static final int MIN_SEARCH_LENGTH = 0;
    private final Publisher<String> publisherAfterInsertData;
    private final Publisher<String> publisherAddStock = new Publisher<>();
    private final ObservableList<StockTransferListItems> observableListTable = FXCollections.observableArrayList();
    private final ObservableList<ItemsModel> sourceItems = FXCollections.observableArrayList();
    private final PauseTransition itemSearchDelay = new PauseTransition(Duration.millis(300));
    private final int codeUpdate;
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockTransferService stockTransferService = ServiceRegistry.get(StockTransferService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private FilteredList<ItemsModel> filteredSourceItems;
    @FXML
    private Button btnAddStock;

    @FXML
    private Button btnPrint;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnClear;

    @FXML
    private Button btnAddSelectedItems;

    @FXML
    private Button btnAddAllItems;

    @FXML
    private Button btnRemoveSelected;

    @FXML
    private Button btnClearItems;

    @FXML
    private SearchableComboBox<String> comboFromStock;

    @FXML
    private SearchableComboBox<String> comboToStock;

    @FXML
    private DatePicker convertDate;

    @FXML
    private StackPane stackPane;

    @FXML
    private TableView<ItemsModel> itemsSearchTable;

    @FXML
    private TableView<StockTransferListItems> tableView;

    @FXML
    private Text stockCount;

    @FXML
    private Text stockTotal;

    @FXML
    private TextField txtCode;

    @FXML
    private TextField txtItemsSearch;

    @FXML
    private GridPane gridPane;

    public ConvertStockMainController(
            Publisher<String> publisherAfterInsertData,
            int codeUpdate
    ) {
        this.publisherAfterInsertData = publisherAfterInsertData;
        this.codeUpdate = codeUpdate;
    }

    @FXML
    public void initialize() {
        otherSetting();
        sourceItemsTableSetting();
        transferTableSetting();
        action();
        updateSummary();

        if (codeUpdate > 0) {
            selectData(codeUpdate);
            applyReadOnlyModeForPostedTransfer();
        }
    }

    private void otherSetting() {
        DateSetting.dateAction(convertDate);

        comboFromStock.setPromptText("اختر المخزن المصدر");
        comboToStock.setPromptText("اختر المخزن المستلم");

        List<String> stockNames = getStockNames();
        comboFromStock.setItems(FXCollections.observableArrayList(stockNames));
        comboToStock.setItems(FXCollections.observableArrayList(stockNames));

        txtCode.setText(String.valueOf(retrieveStockTransferNextCode()));

        btnPrint.setDisable(true);
    }

    @NotNull
    private List<String> getStockNames() {
        try {
            return stockService.getStockNames();
        } catch (DaoException e) {
            logError(e);
            return List.of();
        }
    }

    private void sourceItemsTableSetting() {
        itemsSearchTable.setEditable(false);
        itemsSearchTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        itemsSearchTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<ItemsModel, String> barcodeColumn = new TableColumn<>("الباركود");
        barcodeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(nullToEmpty(cell.getValue().getBarcode())));
        barcodeColumn.setPrefWidth(130);

        TableColumn<ItemsModel, String> itemNameColumn = new TableColumn<>("اسم الصنف");
        itemNameColumn.setCellValueFactory(cell -> cell.getValue().nameItemProperty());
        itemNameColumn.setPrefWidth(260);

        TableColumn<ItemsModel, String> availableColumn = new TableColumn<>("المتاح");
        availableColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatNumber(getAvailableAfterTable(cell.getValue()))));
        availableColumn.setPrefWidth(110);
        availableColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<ItemsModel, String> actionColumn = new TableColumn<>("إضافة");
        actionColumn.setPrefWidth(90);
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button("+");

            {
                button.setMaxWidth(Double.MAX_VALUE);
                button.setStyle("-fx-font-weight: bold;");
                button.setOnAction(event -> {
                    ItemsModel item = getTableView().getItems().get(getIndex());
                    addItemToTransferTable(item, 1);
                });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                setAlignment(Pos.CENTER);
                setGraphic(button);
            }
        });

        itemsSearchTable.getColumns().setAll(
                barcodeColumn,
                itemNameColumn,
                availableColumn,
                actionColumn
        );

        filteredSourceItems = new FilteredList<>(sourceItems, item -> true);
        itemsSearchTable.setItems(filteredSourceItems);
    }

    private void transferTableSetting() {
        new TableColumnAnnotation().getTable(tableView, StockTransferListItems.class);

        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setEditable(true);
        tableView.setItems(observableListTable);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> itemName =
                cell -> cell.getValue().getItem().nameItemProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 1, itemName);

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> barcode =
                cell -> new ReadOnlyStringWrapper(nullToEmpty(cell.getValue().getItem().getBarcode()));
        ColumnSetting.addColumn(tableView, "الباركود", 2, barcode);

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, Double>, ObservableValue<Double>> availableBalance =
                cell -> cell.getValue().getItem().sumAllBalanceProperty().asObject();
        ColumnSetting.addColumn(tableView, "المتاح", 3, availableBalance);

        TableColumn<StockTransferListItems, Double> quantityColumn = new TableColumn<>("الكمية المحولة");
        quantityColumn.setCellValueFactory(cell -> cell.getValue().quantityProperty().asObject());
        quantityColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        quantityColumn.setOnEditCommit(this::changeStockQuantity);
        quantityColumn.setPrefWidth(140);
        quantityColumn.setStyle("-fx-alignment: CENTER;");
        tableView.getColumns().add(quantityColumn);

        TableColumn<StockTransferListItems, String> restColumn = new TableColumn<>("الباقي");
        restColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                formatNumber(cell.getValue().getItem().getSumAllBalance() - cell.getValue().getQuantity())
        ));
        restColumn.setPrefWidth(110);
        restColumn.setStyle("-fx-alignment: CENTER;");
        tableView.getColumns().add(restColumn);

        for (TableColumn<StockTransferListItems, ?> column : tableView.getColumns()) {
            column.setMinWidth(90);
        }

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                removeSelectedRows();
            }
        });
    }

    private void changeStockQuantity(TableColumn.CellEditEvent<StockTransferListItems, Double> event) {
        StockTransferListItems rowValue = event.getRowValue();

        if (rowValue == null || rowValue.getItem() == null) {
            tableView.refresh();
            return;
        }

        double oldQuantity = event.getOldValue() == null ? 1 : event.getOldValue();
        double newQuantity = event.getNewValue() == null ? 0 : event.getNewValue();
        double available = rowValue.getItem().getSumAllBalance();

        if (newQuantity <= 0) {
            AllAlerts.alertError("الكمية يجب أن تكون أكبر من صفر");
            rowValue.setQuantity(oldQuantity);
        } else if (newQuantity > available) {
            AllAlerts.alertError("لا يمكن إدخال كمية أكبر من المتاح");
            rowValue.setQuantity(oldQuantity);
        } else {
            rowValue.setQuantity(newQuantity);
        }

        tableView.refresh();
        itemsSearchTable.refresh();
        updateSummary();
    }

    private void action() {
        publisherAddStock.addObserver(stockName -> {
            if (stockName != null && !stockName.isBlank()) {
                if (!comboFromStock.getItems().contains(stockName)) {
                    comboFromStock.getItems().add(stockName);
                }
                if (!comboToStock.getItems().contains(stockName)) {
                    comboToStock.getItems().add(stockName);
                }
            }
        });

        btnAddStock.setOnAction(event -> openAddStockScreen());

        btnSave.setOnAction(event -> saveTransfer());

        btnClear.setOnAction(event -> clearScreen());

        btnAddSelectedItems.setOnAction(event -> addSelectedSourceItems());

        btnAddAllItems.setOnAction(event -> addAllFilteredItems());

        btnRemoveSelected.setOnAction(event -> removeSelectedRows());

        btnClearItems.setOnAction(event -> clearTransferItems());

        itemSearchDelay.setOnFinished(event -> searchItemsFromDatabase());

        txtItemsSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            itemSearchDelay.stop();
            itemSearchDelay.playFromStart();
        });

        observableListTable.addListener((ListChangeListener<StockTransferListItems>) change -> {
            updateSummary();
            itemsSearchTable.refresh();
        });

        comboFromStock.valueProperty().addListener((observable, oldValue, newValue) -> onSourceStockChanged());

        comboToStock.valueProperty().addListener((observable, oldValue, newValue) -> validateSelectedStocks());

        btnSave.disableProperty().bind(
                comboFromStock.valueProperty().isNull()
                        .or(comboToStock.valueProperty().isNull())
                        .or(Bindings.isEmpty(observableListTable))
        );

        btnAddSelectedItems.disableProperty().bind(
                comboFromStock.valueProperty().isNull()
                        .or(Bindings.isEmpty(itemsSearchTable.getSelectionModel().getSelectedItems()))
        );

        btnAddAllItems.disableProperty().bind(
                comboFromStock.valueProperty().isNull()
                        .or(Bindings.isEmpty(filteredSourceItems))
        );

        btnRemoveSelected.disableProperty().bind(
                Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems())
        );

        btnClearItems.disableProperty().bind(Bindings.isEmpty(observableListTable));

        comboFromStock.disableProperty().bind(Bindings.isNotEmpty(observableListTable));
    }


    private void onSourceStockChanged() {
        sourceItems.clear();
        txtItemsSearch.clear();

        if (comboFromStock.getSelectionModel().isEmpty()) {
            return;
        }

        validateSelectedStocks();
        searchItemsFromDatabase();
    }

    private void searchItemsFromDatabase() {
        if (comboFromStock.getSelectionModel().isEmpty()) {
            sourceItems.clear();
            return;
        }

        try {
            Stock selectedStock = getSelectedStock(comboFromStock.getSelectionModel().getSelectedItem());
            String searchText = txtItemsSearch.getText() == null ? "" : txtItemsSearch.getText().trim();

            if (searchText.length() < MIN_SEARCH_LENGTH) {
                searchText = "";
            }

            List<ItemsModel> items = itemsService.searchAvailableItemsByStockId(
                    selectedStock.getId(),
                    searchText,
                    ITEMS_SEARCH_LIMIT
            );

            sourceItems.setAll(items);
            applyItemsFilter();

        } catch (DaoException e) {
            logError(e);
        }
    }

    private void applyItemsFilter() {
        filteredSourceItems.setPredicate(item -> item != null && getAvailableAfterTable(item) > 0);
    }

    private void addSelectedSourceItems() {
        if (codeUpdate > 0) {
            AllAlerts.alertError("لا يمكن تعديل تحويل مخزني بعد ترحيله");
            return;
        }

        List<ItemsModel> selectedItems = List.copyOf(itemsSearchTable.getSelectionModel().getSelectedItems());

        if (selectedItems.isEmpty()) {
            AllAlerts.alertError("يجب اختيار صنف واحد على الأقل");
            return;
        }

        selectedItems.forEach(item -> addItemToTransferTable(item, 1));

        itemsSearchTable.getSelectionModel().clearSelection();
        itemsSearchTable.refresh();
        updateSummary();
    }

    private void addAllFilteredItems() {
        if (codeUpdate > 0) {
            AllAlerts.alertError("لا يمكن تعديل تحويل مخزني بعد ترحيله");
            return;
        }

        if (filteredSourceItems.isEmpty()) {
            AllAlerts.alertError("لا توجد أصناف متاحة للإضافة");
            return;
        }

        List<ItemsModel> items = List.copyOf(filteredSourceItems);
        items.forEach(item -> addItemToTransferTable(item, 1));

        itemsSearchTable.refresh();
        updateSummary();
    }

    private void addItemToTransferTable(ItemsModel item, double quantity) {
        try {
            if (item == null) {
                return;
            }

            if (comboFromStock.getSelectionModel().isEmpty()) {
                throw new DaoException("يجب اختيار المخزن المصدر أولاً");
            }

            if (comboToStock.getSelectionModel().isEmpty()) {
                throw new DaoException("يجب اختيار المخزن المستلم أولاً");
            }

            validateSelectedStocks();

            StockTransferListItems existingRow = findRowByItemId(item.getId());

            if (existingRow != null) {
                double newQuantity = existingRow.getQuantity() + quantity;

                if (newQuantity > item.getSumAllBalance()) {
                    AllAlerts.alertError("لا يمكن زيادة كمية الصنف عن المتاح: " + item.getNameItem());
                    return;
                }

                existingRow.setQuantity(newQuantity);
                tableView.refresh();
                updateSummary();
                return;
            }

            double availableAfterTable = getAvailableAfterTable(item);

            if (availableAfterTable <= 0) {
                AllAlerts.alertError("لا توجد كمية متاحة للصنف: " + item.getNameItem());
                return;
            }

            if (quantity > availableAfterTable) {
                quantity = availableAfterTable;
            }

            StockTransferListItems transferItem = new StockTransferListItems();
            transferItem.setStock_transfer_id(0);
            transferItem.setItem(item);
            transferItem.setQuantity(quantity);

            observableListTable.add(transferItem);

        } catch (DaoException e) {
            logError(e);
        }
    }

    private StockTransferListItems findRowByItemId(int itemId) {
        return observableListTable.stream()
                .filter(row -> row.getItem() != null)
                .filter(row -> row.getItem().getId() == itemId)
                .findFirst()
                .orElse(null);
    }

    private double getQuantityItemInTable(ItemsModel item) {
        if (item == null) {
            return 0;
        }

        return observableListTable.stream()
                .filter(row -> row.getItem() != null)
                .filter(row -> row.getItem().getId() == item.getId())
                .mapToDouble(StockTransferListItems::getQuantity)
                .sum();
    }

    private double getAvailableAfterTable(ItemsModel item) {
        if (item == null) {
            return 0;
        }

        return item.getSumAllBalance() - getQuantityItemInTable(item);
    }

    private void removeSelectedRows() {
        if (codeUpdate > 0) {
            AllAlerts.alertError("لا يمكن تعديل تحويل مخزني بعد ترحيله");
            return;
        }

        if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
            observableListTable.removeAll(List.copyOf(tableView.getSelectionModel().getSelectedItems()));
            tableView.getSelectionModel().clearSelection();
            itemsSearchTable.refresh();
            applyItemsFilter();
            updateSummary();
        }
    }

    private void clearTransferItems() {
        if (codeUpdate > 0) {
            AllAlerts.alertError("لا يمكن تعديل تحويل مخزني بعد ترحيله");
            return;
        }

        observableListTable.clear();
        itemsSearchTable.refresh();
        applyItemsFilter();
        updateSummary();
    }

    private void saveTransfer() {
        try {
            int result = insertData();

            if (result >= 1) {
                publisherAfterInsertData.notifyObservers();
                AllAlerts.alertSave();
                clearScreenAfterSave();
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private int insertData() throws DaoException {
        if (codeUpdate > 0) {
            throw new DaoException("لا يمكن تعديل تحويل مخزني بعد ترحيله. قم بإلغاء التحويل ثم أنشئ تحويلًا جديدًا.");
        }

        if (observableListTable.isEmpty()) {
            throw new DaoException("يجب إضافة الأصناف أولاً");
        }

        if (comboFromStock.getSelectionModel().isEmpty() || comboToStock.getSelectionModel().isEmpty()) {
            throw new DaoException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        String selectedFrom = comboFromStock.getSelectionModel().getSelectedItem();
        String selectedTo = comboToStock.getSelectionModel().getSelectedItem();

        if (selectedFrom.equals(selectedTo)) {
            throw new DaoException("لا يمكن التحويل إلى نفس المخزن");
        }

        validateQuantitiesBeforeSave();

        if (!AllAlerts.confirmSave()) {
            return 0;
        }

        int stockIdFrom = getSelectedStock(selectedFrom).getId();
        int stockIdTo = getSelectedStock(selectedTo).getId();

        List<StockTransferListItems> items = List.copyOf(observableListTable);

        StockTransfer stockTransfer = stockTransferService.stockTransfer(
                0,
                stockIdFrom,
                stockIdTo,
                convertDate.getValue(),
                items
        );

        return stockTransferService.insertData(stockTransfer);
    }

    private void validateQuantitiesBeforeSave() throws DaoException {
        Set<Integer> itemIds = new HashSet<>();

        for (StockTransferListItems item : observableListTable) {
            if (item.getItem() == null) {
                throw new DaoException("يوجد صنف غير صحيح داخل التحويل");
            }

            if (!itemIds.add(item.getItem().getId())) {
                throw new DaoException("يوجد صنف مكرر داخل التحويل: " + item.getItem().getNameItem());
            }

            if (item.getQuantity() <= 0) {
                throw new DaoException("كمية الصنف يجب أن تكون أكبر من صفر: " + item.getItem().getNameItem());
            }

            if (item.getQuantity() > item.getItem().getSumAllBalance()) {
                throw new DaoException("كمية الصنف أكبر من المتاح: " + item.getItem().getNameItem());
            }
        }
    }

    private void validateSelectedStocks() {
        String fromStock = comboFromStock.getSelectionModel().getSelectedItem();
        String toStock = comboToStock.getSelectionModel().getSelectedItem();

        if (fromStock != null && toStock != null && Objects.equals(fromStock, toStock)) {
            comboToStock.getSelectionModel().clearSelection();
            AllAlerts.alertError("لا يمكن اختيار نفس المخزن كمصدر ومستلم");
        }
    }

    private void openAddStockScreen() {
        try {
            new AddForAllApplication(0, new AddStockController(0, publisherAddStock));
        } catch (Exception e) {
            logError(e);
        }
    }

    private Stock getSelectedStock(String name) throws DaoException {
        if (name == null || name.isBlank()) {
            throw new DaoException("يجب اختيار المخزن");
        }

        Stock stock = stockService.getStockByName(name);

        if (stock == null) {
            throw new DaoException("المخزن غير موجود: " + name);
        }

        return stock;
    }

    private int retrieveStockTransferNextCode() {
        try {
            MaxNumberList<StockTransfer> maxNumberList =
                    new MaxNumberList<>(StockTransfer::getId, stockTransferService.getStockTransferList());
            return maxNumberList.getCode();
        } catch (DaoException e) {
            AllAlerts.alertError(Error_Text_Show.NO_DATA + e.getMessage());
            return 1;
        }
    }

    private void selectData(int id) {
        try {
            StockTransfer stockTransfer = stockTransferService.getStockTransfersById(id);

            txtCode.setText(String.valueOf(stockTransfer.getId()));
            comboFromStock.getSelectionModel().select(stockTransfer.getStockFrom().getName());
            comboToStock.getSelectionModel().select(stockTransfer.getStockTo().getName());
            convertDate.setValue(stockTransfer.getDate());

            observableListTable.clear();

            if (stockTransfer.getTransferListItems() != null) {
                observableListTable.addAll(FXCollections.observableArrayList(stockTransfer.getTransferListItems()));
            }

            updateSummary();

        } catch (DaoException e) {
            logError(e);
        }
    }

    private void clearScreen() {
        if (codeUpdate > 0) {
            return;
        }

        comboFromStock.getSelectionModel().clearSelection();
        comboToStock.getSelectionModel().clearSelection();
        txtItemsSearch.clear();
        sourceItems.clear();
        observableListTable.clear();
        txtCode.setText(String.valueOf(retrieveStockTransferNextCode()));
        updateSummary();
    }

    private void clearScreenAfterSave() {
        observableListTable.clear();
        sourceItems.clear();
        txtItemsSearch.clear();
        comboFromStock.getSelectionModel().clearSelection();
        comboToStock.getSelectionModel().clearSelection();
        txtCode.setText(String.valueOf(retrieveStockTransferNextCode()));
        updateSummary();
    }

    private void updateSummary() {
        stockCount.setText(String.valueOf(observableListTable.size()));
        stockTotal.setText(formatNumber(
                observableListTable.stream()
                        .mapToDouble(StockTransferListItems::getQuantity)
                        .sum()
        ));
    }

    private void applyReadOnlyModeForPostedTransfer() {
        btnSave.setDisable(true);
        btnAddSelectedItems.setDisable(true);
        btnAddAllItems.setDisable(true);
        btnAddStock.setDisable(true);
        btnRemoveSelected.setDisable(true);
        btnClearItems.setDisable(true);
        btnClear.setDisable(true);

        comboFromStock.setDisable(true);
        comboToStock.setDisable(true);
        convertDate.setDisable(true);
        txtItemsSearch.setDisable(true);

        tableView.setEditable(false);
        itemsSearchTable.setDisable(true);
    }

    private boolean containsIgnoreCase(String text, String searchText) {
        return text != null && text.toLowerCase().contains(searchText);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        return String.format("%.3f", value);
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return Setting_Language.STORE_TRANSFERS;
    }

    @Override
    public boolean resize() {
        return true;
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}