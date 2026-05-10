package com.hamza.account.controller.convert_stock;

import com.hamza.account.controller.others.AddStockController;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.controller.search.ItemsSearch;
import com.hamza.account.model.dao.DaoFactory;
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
import com.hamza.account.view.TextSearchApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.others.Utils;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.columnEdit.ColumnSetting;
import com.hamza.controlsfx.util.MaxNumberList;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.SearchableComboBox;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "convert_stocks.fxml")
public class ConvertStockMainController implements AppSettingInterface {

    private final Publisher<String> publisherAfterInsertData;
    private final Publisher<String> publisherAddStock = new Publisher<>();
    private final DaoFactory daoFactory;
    private final ObservableList<StockTransferListItems> observableListTable = FXCollections.observableArrayList();
    private final int codeUpdate;
    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockTransferService stockTransferService = ServiceRegistry.get(StockTransferService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    //    private Items_Stock_Model items_stock_model;
    private StockTransferListItems stockTransferListItems;
    @FXML
    private Button btnAddStock, btnPrint, btnSave, btnAddRow, btnSearch;
    @FXML
    private SearchableComboBox<String> comboFromStock, comboToStock;
    @FXML
    private DatePicker convertDate;
    @FXML
    private StackPane stackPane;
    @FXML
    private TableView<StockTransferListItems> tableView;
    @FXML
    private Text stockCount, stockTotal;
    @FXML
    private TextField txtAvailable, txtCode, txtQuantity, txtRest;
    @FXML
    private GridPane gridPane;
    private StringProperty textSearchItems;
    private BooleanProperty disableButton;

    public ConvertStockMainController(DaoFactory daoFactory, Publisher<String> publisherAfterInsertData, int codeUpdate) throws Exception {
//        super(daoFactory);
        this.daoFactory = daoFactory;
        this.publisherAfterInsertData = publisherAfterInsertData;
        this.codeUpdate = codeUpdate;
    }

    @FXML
    public void initialize() {
        otherSetting();
        tableSetting();
        addTextSearch();
        action();
        if (codeUpdate > 0) {
            selectData(codeUpdate);
        }
    }

    private void addTextSearch() {
        try {
            ItemsSearch itemsSearch = new ItemsSearch(itemsService);
            TextSearchApplication<ItemsModel> itemsTextSearchApplication = new TextSearchApplication<>(itemsSearch);
            textSearchItems = itemsTextSearchApplication.getTextSearchController().textNameProperty();
            disableButton = itemsTextSearchApplication.getTextSearchController().disableButtonProperty();
            // add text search
            gridPane.add(itemsTextSearchApplication.getPane(), 1, 2);
        } catch (Exception e) {
            logError(e);
        }
    }

    private void otherSetting() {
        DateSetting.dateAction(convertDate);
        Utils.setTextFormatter(txtAvailable, txtQuantity, txtRest);
        comboFromStock.setPromptText(Setting_Language.STOCK_NAME);
        comboToStock.setPromptText(Setting_Language.STOCK_NAME);
        comboFromStock.setItems(FXCollections.observableArrayList(getStockNames()));
        comboToStock.setItems(FXCollections.observableArrayList(getStockNames()));
        txtCode.setText(String.valueOf(retrieveTotalBuyMaxNumberListCode()));
    }

    @NotNull
    private List<String> getStockNames() {
        try {
            return stockService.getStockNames();
        } catch (DaoException e) {
            logError(e);
        }
        return List.of();
    }

    private void tableSetting() {
        new TableColumnAnnotation().getTable(tableView, StockTransferListItems.class);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.setEditable(true);
        tableView.setItems(observableListTable);

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, String>, ObservableValue<String>> name = f -> f.getValue().getItem().nameItemProperty();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_NAME, 1, name);

        Callback<TableColumn.CellDataFeatures<StockTransferListItems, Double>, ObservableValue<Double>> balance = f -> f.getValue().getItem().sumAllBalanceProperty().asObject();
        ColumnSetting.addColumn(tableView, Setting_Language.WORD_BALANCE, 2, balance);

        new ColumnSetting().enableDoubleEditing(3, this::changeStockQuantity, tableView);

        for (int i = 0; i < tableView.getColumns().size(); i++) {
            tableView.getColumns().get(i).setMinWidth(80);
        }

        tableView.getColumns().get(1).setPrefWidth(230);
    }

    private void changeStockQuantity(TableColumn.CellEditEvent<StockTransferListItems, Double> t) {
        int row = t.getTablePosition().getRow();
//        t.getTableView().getItems().get(row).setQuantity(t.getNewValue() == null || t.getNewValue() > t.getRowValue().getItem().getSumAllBalance() ? t.getRowValue().getItem().getSumAllBalance() : t.getNewValue());
        t.getTableView().getItems().get(row).setQuantity(t.getNewValue());
        t.getTableView().refresh();
    }

    private void action() {
        publisherAddStock.addObserver(s -> {
            comboFromStock.getItems().add(s);
            comboToStock.getItems().add(s);
        });

        btnAddStock.setOnAction(actionEvent -> {
            try {
                new AddForAllApplication(0, new AddStockController(0, publisherAddStock, daoFactory));
            } catch (Exception e) {
                logError(e);
            }
        });

        tableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.DELETE) {
                if (!tableView.getSelectionModel().getSelectedItems().isEmpty()) {
                    observableListTable.removeAll(tableView.getSelectionModel().getSelectedItems());
                }
            }
        });

        btnSave.setOnAction(actionEvent -> {
            try {
                var i = insertData();
                if (i >= 1) {
                    publisherAfterInsertData.notifyObservers();
                    AllAlerts.alertSave();
                    tableView.getItems().clear();
                }
            } catch (DaoException e) {
                logError(e);
            }
        });

        textSearchItems.addListener((observableValue, s, t1) -> {
            if (t1 != null) {
                try {
                    var stockByName = stockService.getStockByName(comboFromStock.getSelectionModel().getSelectedItem());
                    var itemByItemNameAndStockId = itemsService.getItemByItemNameAndStockId(t1, stockByName.getId());
                    if (itemByItemNameAndStockId != null) {
                        stockTransferListItems = new StockTransferListItems();
                        stockTransferListItems.setStock_transfer_id(0);
                        stockTransferListItems.setItem(itemByItemNameAndStockId);
                        stockTransferListItems.setQuantity(0.0);
                        double sumAllBalance = itemByItemNameAndStockId.getSumAllBalance() - getQuantityItemInTable(stockTransferListItems);
                        txtAvailable.setText(String.valueOf(sumAllBalance));
                    }
                } catch (DaoException e) {
                    logError(e);
                }
            }
        });

        BooleanProperty booleanProperty = new SimpleBooleanProperty(false);
        txtQuantity.setOnKeyPressed(keyEvent -> {
            if (KeyCode.ENTER.equals(keyEvent.getCode())) {
                double quantity = Double.parseDouble(txtQuantity.getText());
                double availableStockAmount = Double.parseDouble(txtAvailable.getText());
                if (quantity > availableStockAmount) {
                    AllAlerts.alertError("لا يمكن ادخال قيمة اكبر من المتاح");
                    booleanProperty.set(true);
                    return;
                }
                txtRest.setText(String.valueOf(availableStockAmount - quantity));
                booleanProperty.set(false);
            }
        });

        btnAddRow.setOnAction(actionEvent -> {
            if (!txtQuantity.getText().isEmpty() || textSearchItems.get().isEmpty())
                try {
                    if (stockTransferListItems != null) {
                        if (checkItemInTable(stockTransferListItems.getItem().getNameItem())) {
                            throw new DaoException("هذا الصنف موجود سابقا ...");
                        }
                        stockTransferListItems.setQuantity(Double.parseDouble(txtQuantity.getText()));
                        observableListTable.add(stockTransferListItems);
                        textSearchItems.set("");
                        reset();
                    }
                } catch (DaoException e) {
                    logError(e);
                }
        });

        observableListTable.addListener((ListChangeListener<StockTransferListItems>) c -> {
            stockCount.setText(String.valueOf(observableListTable.size()));
            stockTotal.setText(String.valueOf(observableListTable.stream().mapToDouble(StockTransferListItems::getQuantity).sum()));
        });


        comboFromStock.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                textSearchItems.set(newValue);
            }
        });

        btnAddRow.disableProperty().bind(booleanProperty.or(txtQuantity.textProperty().isEqualTo("0.0")));
        comboFromStock.disableProperty().bind(stockCount.textProperty().greaterThan("0.0").or(stockTotal.textProperty().greaterThan("0.0")));
        btnSave.disableProperty().bind(stockCount.textProperty().isEmpty().or(stockTotal.textProperty().isEmpty()));
        disableButton.bind(comboFromStock.valueProperty().isNull());
    }

    private double getQuantityItemInTable(StockTransferListItems stockTransferListItems) {
        return tableView.getItems()
                .stream()
                .filter(t1 -> t1.getItem().getNameItem().equals(stockTransferListItems.getItem().getNameItem()))
                .mapToDouble(StockTransferListItems::getQuantity)
                .sum();
    }

    private boolean checkItemInTable(String nameItem) {
        return tableView.getItems().stream().anyMatch(t -> t.getItem().getNameItem().equals(nameItem));
    }

    private void reset() {
        txtAvailable.setText("0");
        txtQuantity.setText("0");
        txtRest.setText("0");
    }

    private Stock getSelectedStock(String name) {
        try {
            return stockService.getStockByName(name);
        } catch (DaoException e) {
            logError(e);
            throw new RuntimeException(e);
        }
    }

    private int retrieveTotalBuyMaxNumberListCode() {
        try {
            MaxNumberList<StockTransfer> totalBuyMaxNumberList = new MaxNumberList<>(StockTransfer::getId, stockTransferService.getStockTransferList());
            return totalBuyMaxNumberList.getCode();
        } catch (DaoException e) {
            AllAlerts.alertError(Error_Text_Show.NO_DATA + e.getMessage());
        }
        return 1;
    }

    private int insertData() throws DaoException {

        if (tableView.getItems().isEmpty())
            throw new DaoException("يجب إضافة الاصناف فى جدول المخزن الاخر اولا");

        if (comboFromStock.getSelectionModel().isEmpty() || comboToStock.getSelectionModel().isEmpty()) {
            throw new DaoException(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
        }

        // check if you want to save data
        if (AllAlerts.confirmSave()) {
            String selectedFrom = comboFromStock.getSelectionModel().getSelectedItem();
            String selectedTo = comboToStock.getSelectionModel().getSelectedItem();
            if (selectedFrom.equals(selectedTo)) throw new DaoException("لا يمكن ادخال نفس المخزن");

            // get stock id
            int stockIdFrom = getSelectedStock(selectedFrom).getId();
            int stockIdTo = getSelectedStock(selectedTo).getId();

            // get list of table
            List<StockTransferListItems> items = tableView.getItems();
            StockTransfer stockTransfer = stockTransferService.stockTransfer(0, stockIdFrom, stockIdTo, convertDate.getValue(), items);
            // insert data
            if (codeUpdate > 0) {
                stockTransfer.setId(codeUpdate);
                return stockTransferService.updateData(stockTransfer);
            } else
                return stockTransferService.insertData(stockTransfer);
        }
        return 0;
    }

    private void selectData(int id) {
        try {
            var stockTransfersById = stockTransferService.getStockTransfersById(id);
            txtCode.setText(String.valueOf(stockTransfersById.getId()));
            comboFromStock.getSelectionModel().select(stockTransfersById.getStockFrom().getName());
            comboToStock.getSelectionModel().select(stockTransfersById.getStockTo().getName());
            convertDate.setValue(stockTransfersById.getDate());
            observableListTable.clear();
            var transferListItems = stockTransfersById.getTransferListItems();
            transferListItems.forEach(t -> t.getItem().getSumAllBalance());
            observableListTable.addAll(FXCollections.observableArrayList(transferListItems));
        } catch (DaoException e) {
            logError(e);
        }
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
        log.error(e.getMessage(), e.getCause());
        AllAlerts.alertError(e.getMessage());
    }
}
