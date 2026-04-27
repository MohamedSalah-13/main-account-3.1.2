package com.hamza.account.controller.items;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadData;
import com.hamza.account.controller.main.MainItems;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.impl_dataInterface.CustomData;
import com.hamza.account.interfaces.impl_dataInterface.CustomDataReturn;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersData;
import com.hamza.account.interfaces.impl_dataInterface.SuppliersDataReturn;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.*;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.ProcessType;
import com.hamza.account.view.ShowInvoiceApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.button.api.ButtonColumnI;
import com.hamza.controlsfx.button.button_column.ButtonColumn;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.util.ImageChoose;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

import static com.hamza.account.type.TypeList.processTypeList;
import static com.hamza.controlsfx.table.Table_Setting.column_number;

@Log4j2
@FxmlPath(pathFile = "items/cardItem-view.fxml")
public class CardController extends LoadData implements Initializable, AppSettingInterface {

    private final int numItem;
    private final MainItems mainItems;
    private final ItemsModel itemsModel;
    @FXML
    private TableView<CardItems> tableView;
    @FXML
    private ComboBox<String> comboBox;
    @FXML
    private Text textPurchase, textSales, textRePurchase, textReSales, textCountTotals, textCostPurchase, textCostSales, textCostSalesRe, textCostPurchaseRe, textCostTotals, textType;
    @FXML
    private Label labelPurchase, labelSales, labelRePurchase, labelReSales, labelFrom, labelTo, labelType, labelName;
    @FXML
    private Button btnSearch, btnPrint;
    @FXML
    private TextField textName;
    @FXML
    private DatePicker dateFrom, dateTo;
    private FilteredList<CardItems> filteredTable;

    public CardController(ItemsModel itemsModel, DaoFactory daoFactory, DataPublisher dataPublisher, MainItems mainItems) throws Exception {
        super(daoFactory, dataPublisher);
        this.numItem = itemsModel.getId();
        this.mainItems = mainItems;
        this.itemsModel = itemsModel;
    }

    public static DataInterface<? extends BasePurchasesAndSales, ?, ?, ?> dataInterface(ProcessType processType, DaoFactory daoFactory, DataPublisher dataPublisher) throws Exception {
        DataInterface<Purchase, Total_buy, Suppliers, SupplierAccount> dataInterfacePurchase = new SuppliersData(daoFactory, dataPublisher);
        DataInterface<Sales, Total_Sales, Customers, CustomerAccount> dataInterfaceSales = new CustomData(daoFactory, dataPublisher);
        DataInterface<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> dataInterfacePurchaseReturn = new SuppliersDataReturn(daoFactory, dataPublisher);
        DataInterface<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> dataInterfaceSalesReturn = new CustomDataReturn(daoFactory, dataPublisher);
        switch (processType) {
            case PURCHASE -> {
                return dataInterfacePurchase;
            }
            case PURCHASE_RETURN -> {
                return dataInterfacePurchaseReturn;
            }
            case SALES -> {
                return dataInterfaceSales;
            }
            case SALES_RETURN -> {
                return dataInterfaceSalesReturn;
            }
            default -> {
                return null;
            }
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        getTable();
        otherSetting();
        addColumnShowInvoice();
        action();
        getSum();
        setFirstDate();
        applyRowColoringForBalance();
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, CardItems.class);
        tableView.getColumns().addFirst(column_number());
        filteredTable = new FilteredList<>(FXCollections.observableList(cardItemsList()), t2 -> true);
        //        FilteredList<BasePurchasesAndSales> filteredTable = new FilteredList<>(FXCollections.observableArrayList(list), p -> true);
        SortedList<CardItems> sortedList = new SortedList<>(filteredTable);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        TableSetting.tableMenuSetting(getClass(), tableView);
    }

    private void setFirstDate() {
        Optional<CardItems> cardItemsList = filteredTable.stream().min(Comparator.comparing(CardItems::getInvoice_date)).stream().findFirst();
        cardItemsList.ifPresent(cardItems -> dateFrom.setValue(LocalDate.parse(cardItems.getInvoice_date().toString())));
    }

    private void otherSetting() {
        btnSearch.setText(Setting_Language.WORD_SEARCH);
        btnPrint.setText(Setting_Language.WORD_PRINT);
        labelPurchase.setText(Setting_Language.WORD_PUR);
        labelSales.setText(Setting_Language.WORD_SALES);
        labelRePurchase.setText(Setting_Language.WORD_RE_PUR);
        labelReSales.setText(Setting_Language.WORD_RE_SALES);
        labelFrom.setText(Setting_Language.WORD_FROM);
        labelTo.setText(Setting_Language.WORD_TO);
        labelType.setText("نوع الفاتورة");
        labelName.setText(Setting_Language.NAME_ITEM);

        comboBox.getItems().add(Setting_Language.WORD_ALL);
        comboBox.getItems().addAll(processTypeList);
        comboBox.getSelectionModel().select(0);

        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        textType.setText(itemsModel.getUnitsType().getUnit_name());
        textName.setText(itemsModel.getNameItem());
    }

    private void action() {
        var image = new Image_Setting();
        btnSearch.setGraphic(ImageChoose.createIcon(image.search));
        btnPrint.setGraphic(ImageChoose.createIcon(image.print));
        btnSearch.setOnAction(actionEvent -> searchAction());
        btnPrint.setOnAction(actionEvent -> print());
    }

    private void print() {
        try {
            List<CardItems> cardItems = cardItemsList();
            ItemsModel itemsModel = itemsService.findItemById(numItem);
            double purchase = cardItems.stream().filter(cardItems1 -> cardItems1.getProcessType().equals(ProcessType.PURCHASE)).mapToDouble(CardItems::getQuantity).sum();
            double sales = cardItems.stream().filter(cardItems1 -> cardItems1.getProcessType().equals(ProcessType.SALES)).mapToDouble(CardItems::getQuantity).sum();
            double purchase_re = cardItems.stream().filter(cardItems1 -> cardItems1.getProcessType().equals(ProcessType.PURCHASE_RETURN)).mapToDouble(CardItems::getQuantity).sum();
            double sales_re = cardItems.stream().filter(cardItems1 -> cardItems1.getProcessType().equals(ProcessType.SALES_RETURN)).mapToDouble(CardItems::getQuantity).sum();

            double amount = itemsModel.getFirstBalanceForStock() + purchase + sales_re - (sales + purchase_re);
            new Print_Reports().printCardItem(numItem, purchase, sales, purchase_re, sales_re, itemsModel.getFirstBalanceForStock()
                    , amount, dateFrom.getValue().toString(), dateTo.getValue().toString());
        } catch (Exception e) {
            logError(e);
        }

    }

    private void applyRowColoringForBalance() {
        tableView.setRowFactory(itemsModelTableView -> {
            TableRow<CardItems> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    if (newValue.getTotals() <= 0.0) {
                        row.setStyle("-fx-background-color: rgba(243,253,163,0.62)");
                    } else {
                        row.setStyle("");
                    }
                }
            });
            return row;
        });

//        new RowColor().customiseRowByRow(tableView, new RowColorInterface<CardItems, Object>() {
//            @Override
//            public boolean checkRow(TableCell<CardItems, Object> tsTableCell) {
//                return tsTableCell.getTableRow().getItem().getTotals() <= 0.0;
//            }
//        });

    }

    private void searchAction() {
        filteredTable.setPredicate((filterByComboName(comboBox.getSelectionModel().getSelectedItem())).and(filterByDate()));
//        SortedList<CardItems> sortedList = new SortedList<>(filteredTable);
//        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
//        tableView.setItems(sortedList);
        tableView.refresh();
        getSum();
    }

    private Predicate<CardItems> filterByComboName(String nameProcess) {
        if (!comboBox.getSelectionModel().isEmpty()) {
            if (comboBox.getSelectionModel().getSelectedIndex() == 0) return t2 -> true;
            return cardItems -> cardItems.getProcessType().getType().equals(nameProcess);
        }
        return t2 -> false;
    }

    private Predicate<CardItems> filterByDate() {
        String dateFromValue = dateFrom.getValue().toString();
        String dateToValue = dateTo.getValue().toString();
        return t2 -> (LocalDate.parse(t2.getInvoice_date().toString()).isEqual(LocalDate.parse(dateFromValue)) || LocalDate.parse(t2.getInvoice_date().toString()).isAfter(LocalDate.parse(dateFromValue)))
                && (LocalDate.parse(t2.getInvoice_date().toString()).isEqual(LocalDate.parse(dateToValue)) || LocalDate.parse(t2.getInvoice_date().toString()).isBefore(LocalDate.parse(dateToValue)));
    }

    private List<CardItems> cardItemsList() {
        List<CardItems> cardItems = getCardItems();
        filterCardItemsByProcessType(cardItems, true, ProcessType.PURCHASE);
        filterCardItemsByProcessType(cardItems, true, ProcessType.SALES);
        filterCardItemsByProcessType(cardItems, true, ProcessType.PURCHASE_RETURN);
        filterCardItemsByProcessType(cardItems, true, ProcessType.SALES_RETURN);
        return cardItems;
    }

    @NotNull
    private List<CardItems> getCardItems() {
        List<CardItems> cardItems = new ArrayList<>();
        try {
            cardItems = cardItemService.cardItemsListByNumItem(numItem)
                    .stream()
                    .sorted(Comparator.comparing(CardItems::getCreated_at)).toList();
        } catch (Exception e) {
            logError(e);
        }
        return cardItems;
    }

    private void filterCardItemsByProcessType(List<CardItems> cardItems, boolean isAllowed, ProcessType processType) {
        if (!isAllowed) {
            cardItems.stream()
                    .filter(cardItem -> !cardItem.getProcessType().equals(processType)).toList();
        }
    }

    private void getSum() {
        var purchase = extracted(ProcessType.PURCHASE);
        var sales = extracted(ProcessType.SALES);
        var purchaseReturn = extracted(ProcessType.PURCHASE_RETURN);
        var salesReturn = extracted(ProcessType.SALES_RETURN);

        textPurchase.setText(String.valueOf(purchase));
        textSales.setText(String.valueOf(sales));
        textRePurchase.setText(String.valueOf(purchaseReturn));
        textReSales.setText(String.valueOf(salesReturn));
        textCountTotals.setText(String.valueOf((purchase + salesReturn) - (sales + purchaseReturn)));

        textCostPurchase.setText(String.valueOf(sumTotals(ProcessType.PURCHASE)));
        textCostSales.setText(String.valueOf(sumTotals(ProcessType.SALES)));
        textCostSalesRe.setText(String.valueOf(sumTotals(ProcessType.SALES_RETURN)));
        textCostPurchaseRe.setText(String.valueOf(sumTotals(ProcessType.PURCHASE_RETURN)));
//        textCostTotals.setText(String.valueOf((sumTotals(ProcessType.PURCHASE) + sumTotals(ProcessType.SALES_RETURN)) - (sumTotals(ProcessType.SALES) + sumTotals(ProcessType.PURCHASE_RETURN))));

        // ارباح الصنف
        // سعر الشراء من المبيعات والبيع
        var buyPriceSales = tableView.getItems().stream().filter(cardItems -> cardItems.getProcessType() == ProcessType.SALES).mapToDouble(CardItems::getProfit).sum();
        var buyPriceSalesReturn = tableView.getItems().stream().filter(cardItems -> cardItems.getProcessType() == ProcessType.SALES_RETURN).mapToDouble(CardItems::getProfit).sum();
        textCostTotals.setText(String.valueOf(buyPriceSales - buyPriceSalesReturn));


    }

    private double extracted(ProcessType processType) {
//        return tableView.getItems().stream().filter(cardItems -> cardItems.getProcessType() == processType).mapToDouble(CardItems::getQuantity).sum();
        return tableView.getItems().stream()
                .filter(cardItems -> cardItems.getProcessType() == processType)
                .mapToDouble(value -> {
                    try {
                        return value.getQuantity() * unitsService.getUnitsByName(value.getType_name()).getValue();
                    } catch (DaoException e) {
                        logError(e);
                        return 0;
                    }
                }).sum();
    }

    private double sumTotals(ProcessType processType) {
        return tableView.getItems().stream().filter(cardItems -> cardItems.getProcessType() == processType).mapToDouble(CardItems::getTotals).sum();
    }

    private void addColumnShowInvoice() {
        tableView.getColumns().add(new ButtonColumn<>(new ButtonColumnI() {
            @Override
            public void action(int index) {
                try {
                    CardItems cardItems = tableView.getItems().get(index);
                    int id = cardItems.getInvoice_num();
                    String name = cardItems.getNameItem();
                    ProcessType processType = cardItems.getProcessType();
                    new ShowInvoiceApplication<>(dataPublisher, dataInterface(processType, daoFactory, dataPublisher), daoFactory, id, name);
                } catch (Exception e) {
                    logError(e);
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
                return Setting_Language.WORD_SHOW;
            }
        }));
    }

    @Override
    public @NotNull Pane pane() throws IOException {
        return new OpenFxmlApplication(this).getPane();
    }

    @Override
    public String title() {
        return Setting_Language.WORD_CARD_ITEM;
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
