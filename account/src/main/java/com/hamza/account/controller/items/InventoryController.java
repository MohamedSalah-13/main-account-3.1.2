package com.hamza.account.controller.items;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.InventoryItemModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.InventoryService;
import com.hamza.account.service.StockService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.account.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.NumberUtils;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.function.Function;

@Log4j2
@FxmlPath(pathFile = "items/inventory-view.fxml")
public class InventoryController {

    private final DataPublisher dataPublisher;
    private final ObservableList<String> observableList = FXCollections.observableArrayList();
    private final TableView<InventoryItemModel> tableView = new TableView<>();
    private final int ROWS_PER_PAGE = 50;

    private final InventoryService inventoryService = ServiceRegistry.get(InventoryService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);

    @FXML
    private ComboBox<String> comboStock;

    @FXML
    private TextField textSearch;

    @FXML
    private Text textSumPurchase, textSumSales;

    @FXML
    private Button btnPrint, btnRefresh;

    @FXML
    private Pagination pagination;

    public InventoryController(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }

    @FXML
    public void initialize() {
        addComboStock();
        setupTable();
        setupActions();
        refreshData();
        dataPublisher.getPublisherAddStock().addObserver(message -> addComboStock());
    }

    private void setupTable() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        addColumnData("الكود", InventoryItemModel::getItemId);
        addColumnData("الباركود", InventoryItemModel::getBarcode);
        addColumnData("اسم الصنف", InventoryItemModel::getNameItem);
        addColumnData("المخزن", InventoryItemModel::getStockName);
        addColumnData("الوحدة", InventoryItemModel::getUnitName);

        addColumnData("رصيد أول", InventoryItemModel::getFirstBalance);
        addColumnData("مشتريات", InventoryItemModel::getQuantityPurchase);
        addColumnData("مرتجع مبيعات", InventoryItemModel::getQuantitySalesRe);
        addColumnData("تحويلات واردة", InventoryItemModel::getTransferIn);

        addColumnData("مبيعات", InventoryItemModel::getQuantitySales);
        addColumnData("مرتجع مشتريات", InventoryItemModel::getQuantityPurchaseRe);
        addColumnData("تحويلات صادرة", InventoryItemModel::getTransferOut);

        addColumnData("الرصيد", InventoryItemModel::getCurrentBalance);
        addColumnData("سعر الشراء", InventoryItemModel::getBuyPrice);
        addColumnData("إجمالي الشراء", InventoryItemModel::getStockValueCost);
        addColumnData("سعر البيع", InventoryItemModel::getSellPrice);
        addColumnData("إجمالي البيع", InventoryItemModel::getStockValueSell);

        TableColumn<InventoryItemModel, String> statusColumn = new TableColumn<>("الحالة");
        statusColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(getStockStatusArabic(cell.getValue().getStockStatus())));
        tableView.getColumns().add(statusColumn);
    }

    private void setupActions() {
        comboStock.setItems(observableList);

        comboStock.valueProperty().addListener((observable, oldValue, newValue) -> refreshData());

        btnRefresh.setOnAction(event -> refreshData());

        btnPrint.setOnAction(actionEvent ->
                new Print_Reports().printInventoryByTable(
                        FXCollections.observableArrayList(),
                        comboStock.getSelectionModel().getSelectedItem()
                )
        );

        btnPrint.disableProperty().bind(comboStock.valueProperty().isNull());

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        textSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> refreshData());
            pause.playFromStart();
        });
    }

    private void refreshData() {
        try {
            int totalItems = inventoryService.countInventory(getSelectedStockName(), getSearchText());
            int pageCount = Math.max(1, (int) Math.ceil((double) totalItems / ROWS_PER_PAGE));

            pagination.setPageCount(pageCount);
            pagination.setCurrentPageIndex(0);
            pagination.setPageFactory(pageIndex -> {
                loadPage(pageIndex);
                return tableView;
            });

            loadPage(0);
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void loadPage(int pageIndex) {
        try {
            int offset = pageIndex * ROWS_PER_PAGE;

            List<InventoryItemModel> data = inventoryService.getInventory(
                    getSelectedStockName(),
                    getSearchText(),
                    ROWS_PER_PAGE,
                    offset
            );

            tableView.setItems(FXCollections.observableArrayList(data));
            calculateTotalBalances();
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void addComboStock() {
        try {
            observableList.clear();
            observableList.add(Setting_Language.WORD_ALL);
            observableList.addAll(stockService.getStockNames());

            if (comboStock.getSelectionModel().isEmpty()) {
                comboStock.getSelectionModel().selectFirst();
            }
        } catch (DaoException e) {
            logError(e);
        }
    }

    private void calculateTotalBalances() {
        double totalCost = tableView.getItems()
                .stream()
                .mapToDouble(InventoryItemModel::getStockValueCost)
                .sum();

        double totalSell = tableView.getItems()
                .stream()
                .mapToDouble(InventoryItemModel::getStockValueSell)
                .sum();

        textSumPurchase.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(totalCost)));
        textSumSales.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(totalSell)));
    }

    private String getSelectedStockName() {
        return comboStock.getSelectionModel().getSelectedItem();
    }

    private String getSearchText() {
        return textSearch.getText() == null ? "" : textSearch.getText().trim();
    }

    private String getStockStatusArabic(String status) {
        if ("OUT_OF_STOCK".equalsIgnoreCase(status)) {
            return "غير متوفر";
        }

        if ("LOW".equalsIgnoreCase(status)) {
            return "منخفض";
        }

        return "جيد";
    }

    private <T> void addColumnData(String name, Function<InventoryItemModel, T> valueExtractor) {
        TableColumn<InventoryItemModel, T> colName = new TableColumn<>(name);
        colName.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(valueExtractor.apply(cell.getValue())));
        colName.setMinWidth(90);
        tableView.getColumns().add(colName);
    }

    private void logError(Exception e) {
        log.error(e.getMessage(), e);
        AllAlerts.alertError(e.getMessage());
    }
}