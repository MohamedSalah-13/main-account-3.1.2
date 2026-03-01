package com.hamza.account.controller.items;

import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.AddColumnMix;
import com.hamza.controlsfx.table.ColumnInterface;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.text.NumberUtils;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.language.Setting_Language.*;
import static com.hamza.controlsfx.table.TextSearch.searchTableFromExitedText;

@Log4j2
@FxmlPath(pathFile = "items/inventory-view.fxml")
public class InventoryController extends ServiceData {

    private final DataPublisher dataPublisher;
    private final ObservableList<String> observableList = FXCollections.observableArrayList();
    private final ObservableList<ItemsModel> observableListTable = FXCollections.observableArrayList();
    private MaskerPaneSetting maskerPaneSetting;
    @FXML
    private TableView<ItemsModel> tableView;
    @FXML
    private ComboBox<String> comboStock;
    @FXML
    private Label labelStock, labelSearch, labelSumPurchase, labelSumSales;
    @FXML
    private TextField textSearch;
    @FXML
    private Text textSumPurchase, textSumSales;
    @FXML
    private Button btnPrint, btnRefresh;
    @FXML
    private StackPane stackPane;
    @FXML
    private CheckBox checkShowZeroBalance;
    private FilteredList<ItemsModel> filteredTable;

    public InventoryController(DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        super(daoFactory);
        this.dataPublisher = dataPublisher;
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        nameSetting();
        addComboStock();
        actionButton();
        getTable();
        loadData();
        calculatePurchaseAndSalesTotal();
        dataPublisher.getPublisherAddStock().addObserver(message -> addComboStock());
    }

    private void nameSetting() {
        labelStock.setText(STOCK_NAME);
        labelSearch.setText(WORD_SEARCH);
        labelSumPurchase.setText(TOTAL_BY_PUR);
        labelSumSales.setText(TOTAL_BY_SALES);
        textSearch.setPromptText(WORD_SEARCH);
        comboStock.setPromptText(STOCK_NAME);
        btnPrint.setText(WORD_PRINT);
        btnRefresh.setText(WORD_REFRESH);
        checkShowZeroBalance.setText(Setting_Language.SHOW_BALANCE_ZERO);
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
        filteredTable = new FilteredList<>(observableListTable);

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

    private void loadData() {
        maskerPaneSetting.showMaskerPane(() -> {
            var processesData = itemsService.getMainItemsListWithoutInactive();
            observableListTable.setAll(processesData);
        });
    }

    private void searchAction() {
        filteredTable.setPredicate(getAdminPredicate().and(filterItemsByBalance()));
        SortedList<ItemsModel> sortedList = new SortedList<>(filteredTable);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);
    }

    private Predicate<ItemsModel> getAdminPredicate() {
        if (!comboStock.getSelectionModel().isEmpty()) {
            if (comboStock.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return itemsModel -> itemsModel.getItemStock().getName().equals(comboStock.getSelectionModel().getSelectedItem());
        }
        return t2 -> false;
    }

    private Predicate<ItemsModel> filterItemsByBalance() {
        if (checkShowZeroBalance.isSelected()) return itemsModel -> true;
        return itemsModel -> itemsModel.getSumAllBalance() != 0;
    }

    private void addComboStock() {
        try {
            observableList.clear();
            observableList.addAll(stockService.getStockNames());
            observableList.addFirst(Setting_Language.WORD_ALL);
            comboStock.getSelectionModel().selectFirst();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.alertError(e.getMessage());
        }
    }

    private void actionButton() {
        comboStock.setItems(observableList);
        comboStock.valueProperty().addListener((observableValue, s, t1) -> searchAction());
        checkShowZeroBalance.selectedProperty().addListener((observableValue, s, t1) -> searchAction());
        btnRefresh.setOnAction(actionEvent -> loadData());
        btnPrint.setOnAction(actionEvent -> new Print_Reports().printInventoryByTable(tableView.getItems(), comboStock.getSelectionModel().getSelectedItem()));
        btnPrint.disableProperty().bind(comboStock.valueProperty().isNull());
        textSearch.setOnKeyReleased(event -> searchTableFromExitedText(tableView, textSearch.getText(), observableListTable));
    }

    private void calculatePurchaseAndSalesTotal() {
        filteredTable.addListener((ListChangeListener<? super ItemsModel>) change -> {
            double v2 = filteredTable.stream().mapToDouble(ItemsModel::getSumAllBalanceByBuyPrice).sum();
            double v3 = filteredTable.stream().mapToDouble(ItemsModel::getSumAllBalanceBySelPrice).sum();
            textSumPurchase.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(v2)));
            textSumSales.setText(String.valueOf(NumberUtils.roundToTwoDecimalPlaces(v3)));
        });
    }

    private <T> void addColumnData(String name, Callback<TableColumn.CellDataFeatures<ItemsModel, T>, ObservableValue<T>> column) {
        TableColumn<ItemsModel, T> colName = new TableColumn<>(name);
        colName.setCellValueFactory(column);
        tableView.getColumns().add(colName);
    }
}
