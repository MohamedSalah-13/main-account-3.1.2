package com.hamza.account.controller.items;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.ItemCardModel;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemCardService;
import com.hamza.account.service.ItemsService;
import com.hamza.account.service.StockService;
import com.hamza.account.database.DaoException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class ItemCardController implements Initializable {

    private final ItemsService itemsService = ServiceRegistry.get(ItemsService.class);
    private final StockService stockService = ServiceRegistry.get(StockService.class);
    private final ItemCardService itemCardService = ServiceRegistry.get(ItemCardService.class);
    @FXML
    private ComboBox<String> itemCombo;
    @FXML
    private ComboBox<String> stockCombo;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TableView<ItemCardModel> tableView;
    @FXML
    private Label openingBalanceLabel;
    @FXML
    private Label closingBalanceLabel;
    // Table columns (defined in FXML)
    @FXML
    private TableColumn<ItemCardModel, LocalDate> colDate;
    @FXML
    private TableColumn<ItemCardModel, String> colMovementType;
    @FXML
    private TableColumn<ItemCardModel, Number> colIn;
    @FXML
    private TableColumn<ItemCardModel, Number> colOut;
    @FXML
    private TableColumn<ItemCardModel, Number> colBalance;
    @FXML
    private TableColumn<ItemCardModel, Number> colInvoiceNo;
    @FXML
    private TableColumn<ItemCardModel, String> colParty;
    @FXML
    private TableColumn<ItemCardModel, Number> colPrice;
    @FXML
    private TableColumn<ItemCardModel, String> colUnit;
    @FXML
    private TableColumn<ItemCardModel, String> colUser;
    @FXML
    private TableColumn<ItemCardModel, String> colNotes;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize services
        setupTableColumns();
        loadStocks();
        setupItemCombo();
        loadData();
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("movementDate"));
        colMovementType.setCellValueFactory(new PropertyValueFactory<>("movementTypeAr"));
        colIn.setCellValueFactory(new PropertyValueFactory<>("quantityIn"));
        colOut.setCellValueFactory(new PropertyValueFactory<>("quantityOut"));
        colBalance.setCellValueFactory(new PropertyValueFactory<>("runningBalance"));
        colInvoiceNo.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));
        colParty.setCellValueFactory(new PropertyValueFactory<>("partyName"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        colUnit.setCellValueFactory(new PropertyValueFactory<>("unitName"));
        colUser.setCellValueFactory(new PropertyValueFactory<>("userName"));
        colNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
    }

    private void loadStocks() {
        try {
            List<String> stocks = stockService.getStockNames(); // يحتاج تنفيذ في StockService
            stockCombo.getItems().setAll(stocks);
            stockCombo.getItems().addFirst("جميع المخازن");
            stockCombo.getSelectionModel().selectFirst();
        } catch (DaoException e) {
//            AlertUtil.showError("خطأ في تحميل المخازن", e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    private void setupItemCombo() {
        // Enable search as you type
        itemCombo.setEditable(true);
        itemCombo.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.length() >= 2) {
                searchItems(newVal);
            }
        });
        // When an item is selected, load its data
        itemCombo.setOnAction(e -> loadData());
    }

    private void searchItems(String searchText) {
        try {
            List<ItemsModel> items = itemsService.getFilterItems(searchText);
            ObservableList<String> options = FXCollections.observableArrayList(items.stream().map(ItemsModel::getNameItem).toList());
            itemCombo.setItems(options);
            itemCombo.show();
        } catch (DaoException ex) {
//            AlertUtil.showError("خطأ في البحث", ex.getMessage());
            System.out.println(ex.getMessage());
        }

    }

    @FXML
    private void handleSearch() {
        loadData();
    }

    private void loadData() {
        try {
            if (itemCombo.getValue() == null || itemCombo.getValue().isEmpty()) {
//            AlertUtil.showWarning("تنبيه", "الرجاء اختيار صنف أولاً");
                System.out.println("Please select an item first.");
                return;
            }

            Integer itemId = itemsService.getFilterItems(itemCombo.getValue()).get(0).getId();
            Integer stockId = stockCombo.getSelectionModel().getSelectedIndex() == 0 ? null : stockService.getStockByName(stockCombo.getSelectionModel().getSelectedItem()).getId();
//            if (stockCombo.getValue() != null || stockCombo.getSelectionModel().getSelectedIndex() !=0) {
//                System.out.println(stockCombo.getSelectionModel().getSelectedItem());
//                System.out.println(stockCombo.getValue());
//                stockId = stockService.getStockByName(stockCombo.getSelectionModel().getSelectedItem()).getId();
//            }
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();


            List<ItemCardModel> movements = itemCardService.getItemMovements(itemId, stockId, startDate, endDate);
            tableView.getItems().setAll(movements);

            // حساب الرصيد الافتتاحي والختامي
            if (!movements.isEmpty()) {
                double opening = movements.getFirst().getRunningBalance() -
                        (movements.getFirst().getQuantityIn() - movements.getFirst().getQuantityOut());
                openingBalanceLabel.setText(String.format("%.3f", opening));
                double closing = movements.getLast().getRunningBalance();
                closingBalanceLabel.setText(String.format("%.3f", closing));
            } else {
                openingBalanceLabel.setText("0");
                closingBalanceLabel.setText("0");
            }
        } catch (DaoException e) {
//            AlertUtil.showError("خطأ في تحميل البيانات", e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        // تنفيذ تصدير إلى Excel باستخدام Apache POI أو مكتبة مشابهة
//        AlertUtil.showInfo("تصدير", "سيتم تنفيذ تصدير Excel لاحقاً");
    }
}
