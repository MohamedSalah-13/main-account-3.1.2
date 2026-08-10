package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.StockService;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.AddColumnMix;
import com.hamza.controlsfx.table.ColumnInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.NumberUtils;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.util.Duration;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.List;

import static com.hamza.controlsfx.language.Setting_Language.*;

@Log4j2
@FxmlPath(pathFile = "items/inventory-view.fxml")
public class InventoryController {

    private final TableView<ItemsModel> tableView = new TableView<>();
    private final int ROWS_PER_PAGE = 50;

    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    @FXML
    private TextField textSearch;
    @FXML
    private Text textSumPurchase, textSumSales;
    @FXML
    private Button btnPrint, btnRefresh;
    @FXML
    private Pagination pagination;

    @FXML
    public void initialize() {
        actionButton();
        getTable();
        initializePagination();
    }

    private void initializePagination() {
        int totalItems = itemsService.getCountItems(); // database.getCount();
        int pageCount = (totalItems / ROWS_PER_PAGE) + 1;
        pagination.setPageCount(pageCount);
        // 3. تحديد ماذا يحدث عند تغيير الصفحة (Factory)
        pagination.setPageFactory((pageIndex) -> {
            updateTableView(pageIndex);
            return tableView; // نعيد الجدول ليتم عرضه داخل صفحة الـ Pagination
        });
    }

    private void updateTableView(int pageIndex) {
        try {
            int offset = pageIndex * ROWS_PER_PAGE;
            // هنا الكود الحقيقي لجلب البيانات من قاعدة البيانات
            List<ItemsModel> data = itemsService.getProducts(ROWS_PER_PAGE, offset);
            tableView.setItems(FXCollections.observableArrayList(data));
        } catch (DaoException e) {
            throw new RuntimeException(e);
        }
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, ItemsModel.class);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnFirstBalance = f -> f.getValue().firstBalanceForStockProperty().asObject();
        addColumnData(FIRST_BALANCE, columnFirstBalance);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnPur = f -> f.getValue().sumPurchaseProperty().asObject();
        addColumnData(Setting_Language.WORD_PUR, columnPur);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnSales = f -> f.getValue().sumSalesProperty().asObject();
        addColumnData(Setting_Language.WORD_SALES, columnSales);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnPurRe = f -> f.getValue().sumPurchaseReProperty().asObject();
        addColumnData(RETURN + "\n" + Setting_Language.WORD_PUR, columnPurRe);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnSalesRe = f -> f.getValue().sumSalesReProperty().asObject();
        addColumnData(RETURN + "\n" + Setting_Language.WORD_SALES, columnSalesRe);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnRest = f -> f.getValue().fromStockProperty().asObject();
        addColumnData("تحويلات صادرة", columnRest);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnTo = f -> f.getValue().toStockProperty().asObject();
        addColumnData("تحويلات واردة", columnTo);

        Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>> columnBalance = f -> f.getValue().sumAllBalanceProperty().asObject();
        addColumnData(BALANCE_NOW, columnBalance);

        // add column price
        addColumnPrice();

        tableView.getColumns().get(2).setPrefWidth(250);
        List<Integer> list = List.of(5, 4, 3, 1);
        tableView.getColumns().removeAll(list.stream().map(tableView.getColumns()::get).toList());
        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void addColumnPrice() {
        var columnInterface = new ColumnInterface<ItemsModel, Double>() {
            @Override
            public HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>>> STRING_CALLBACK_HASH_MAP() {
                HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>>> hashMap = new HashMap<>();
                hashMap.put(PRICE, f -> f.getValue().buyPriceProperty().asObject());
                hashMap.put(TOTAL, f -> f.getValue().sumAllBalanceByBuyPriceProperty().asObject());
                return hashMap;
            }
        };
        tableView.getColumns().add(new AddColumnMix<ItemsModel, Double>().getTableColumn(Setting_Language.PURCHASE, columnInterface));

        var columnInterfaceSales = new ColumnInterface<ItemsModel, Double>() {
            @Override
            public HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>>> STRING_CALLBACK_HASH_MAP() {
                HashMap<String, Callback<TableColumn.CellDataFeatures<ItemsModel, Double>, ObservableValue<Double>>> hashMap = new HashMap<>();
                hashMap.put(PRICE, f -> f.getValue().selPrice1Property().asObject());
                hashMap.put(TOTAL, f -> f.getValue().sumAllBalanceBySelPriceProperty().asObject());
                return hashMap;
            }
        };
        tableView.getColumns().add(new AddColumnMix<ItemsModel, Double>().getTableColumn(SALES, columnInterfaceSales));
    }

    /**
     * Header for the printed inventory sheet. This used to be whichever warehouse
     * the user had picked; with a single stock it is simply that stock's name, read
     * from the database so a renamed stock still prints correctly.
     */
    private String stockName() {
        try {
            return stockService.getDefaultStock().getName();
        } catch (DaoException e) {
            log.error(e.getMessage(), e);
            return "";
        }
    }

    private void actionButton() {
//        checkShowZeroBalance.selectedProperty().addListener((observableValue, s, t1) -> searchAction());
        pagination.currentPageIndexProperty().addListener((observableValue, s, t1) -> updateTableView(t1.intValue()));
        btnPrint.setOnAction(actionEvent -> new Print_Reports().printInventoryByTable(tableView.getItems(), stockName()));

        pagination.currentPageIndexProperty().addListener((observableValue, s, t1) -> {
            calculateTotalBalances();
        });

        PauseTransition pause = new PauseTransition(Duration.millis(500));
        textSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            pause.setOnFinished(event -> {
                try {
                    loadDataFromDB(newValue); // لا يتم الاستدعاء إلا بعد التوقف عن الكتابة
                    calculateTotalBalances();
                } catch (DaoException e) {
                    log.error(e.getMessage(), e);
                    AllAlerts.alertError(e.getMessage());
                }
            });
            pause.playFromStart();
        });
    }

    private void loadDataFromDB(String newValue) throws DaoException {
        var filterItems = itemsService.getFilterItems(newValue);
        tableView.setItems(FXCollections.observableArrayList(filterItems));
    }


    private void calculateTotalBalances() {
        double v2 = tableView.getItems().stream().mapToDouble(ItemsModel::getSumAllBalanceByBuyPrice).sum();
        double v3 = tableView.getItems().stream().mapToDouble(ItemsModel::getSumAllBalanceBySelPrice).sum();
        textSumPurchase.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(v2)));
        textSumSales.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(v3)));
    }

    private <T> void addColumnData(String name, Callback<TableColumn.CellDataFeatures<ItemsModel, T>, ObservableValue<T>> column) {
        TableColumn<ItemsModel, T> colName = new TableColumn<>(name);
        colName.setCellValueFactory(column);
        tableView.getColumns().add(colName);
    }
}
