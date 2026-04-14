package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Sales_Package;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.otherSetting.MaskerPaneSetting;
import com.hamza.account.reportData.Print_Reports;
import com.hamza.account.table.TableSetting;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.others.DateSetting;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.hamza.controlsfx.util.ImageChoose.createIcon;

@Log4j2
@FxmlPath(pathFile = "reports/report-items.fxml")
public class ReportItemsController extends ServiceData {

    @FXML
    private ComboBox<String> comboType;
    @FXML
    private DatePicker dateFrom, dateTo;
    @FXML
    private Button btnSearch, btnPrint;
    @FXML
    private TableView<ItemsModel> tableView;
    @FXML
    private StackPane stackPane;
    @FXML
    private RadioButton radioAll, radioItems, radioGroups;
    private MaskerPaneSetting maskerPaneSetting;

    public ReportItemsController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @FXML
    public void initialize() {
        maskerPaneSetting = new MaskerPaneSetting(stackPane);
        DateSetting.dateAction(dateFrom);
        DateSetting.dateAction(dateTo);
        table_data();
        buttonGraphic();
        btnSearch.setOnAction(event -> maskerPaneSetting.showMaskerPane(this::searchAction));
        btnPrint.setOnAction(event -> printInvoice());
        comboType.getItems().setAll("أكثر الاصناف مبيعا");
        comboType.getSelectionModel().selectFirst();
        applyRowColoringForBalance();
    }

    private void buttonGraphic() {
        var images = new Image_Setting();
        btnSearch.setGraphic(createIcon(images.search));
        btnPrint.setGraphic(createIcon(images.print));
    }

    private void table_data() {
        new TableColumnAnnotation().getTable(tableView, ItemsModel.class);

        tableView.getColumns().retainAll(
                tableView.getColumns().get(0),  // ID column
                tableView.getColumns().get(2),  // Name column
                tableView.getColumns().get(7)   // Quantity column
        );

        TableColumn<ItemsModel, Double> columnType = (TableColumn<ItemsModel, Double>) tableView.getColumns().get(2);
        columnType.setText("الكمية");
        TableSetting.tableMenuSetting(getClass(), tableView);

    }

    private void printInvoice() {
        if (dateFrom.getValue() == null || dateTo.getValue() == null)
            return;
        if (dateFrom.getValue().isAfter(dateTo.getValue())) {
            AllAlerts.alertError("Date from must be before date to");
            return;
        }
        if (tableView.getItems().isEmpty()) {
            AllAlerts.alertError("No data to print");
            return;
        }
        Print_Reports printReports = new Print_Reports();
        printReports.printReceiptItemsQuantity(tableView.getItems(), dateFrom.getValue().toString(), dateTo.getValue().toString());
    }

    private void searchAction() {
        try {
            if (dateFrom.getValue() == null || dateTo.getValue() == null)
                throw new DaoException("Date from and date to must be selected");
            if (dateFrom.getValue().isAfter(dateTo.getValue())) {
                throw new DaoException("Date from must be before date to");
            }

            var listTotalSalesId = totalSalesService.getTotalSalesByDateRange(dateFrom.getValue().toString(), dateTo.getValue().toString())
                    .stream()
                    .map(BaseTotals::getId)
                    .toList();
            if (listTotalSalesId.isEmpty()) {
                throw new DaoException("لا يوجد فواتير بين هذا التاريخ");
            }

            var list = salesService.findBetweenTwoInvoiceNumber(listTotalSalesId.getFirst(), listTotalSalesId.getLast());

            // add items from sales package
            List<Sales> list1 = new ArrayList<>();
            list.forEach(sales -> {
                if (sales.isItem_has_package()) {
                    try {
                        var salesPackages = salesPackageService.fetchByInvoiceNumber(sales.getId());
                        for (Sales_Package salesPackage : salesPackages) {
                            Sales salesFrom = new Sales();
                            salesFrom.setNumItem(salesPackage.getItems_id());
                            salesFrom.setQuantityByUnit(salesPackage.getQuantity());
                            list1.add(salesFrom);
                        }
                    } catch (DaoException e) {
                        AllAlerts.alertError("Error fetching sales packages for invoice: " + sales.getId());
                        log.error("Error fetching sales packages for invoice: {}", sales.getId(), e);
                    }
                }
            });

            list.addAll(list1);

            // group by item id
            var itemQuantities = list.stream()
                    .collect(Collectors.groupingBy(Sales::getNumItem,
                            Collectors.summingDouble(Sales::getQuantityByUnit)));

            List<ItemsModel> itemsModelObservableList = new ArrayList<>();

            for (var entry : itemQuantities.entrySet()) {
                var item = itemsService.getItemByItemIdAndStockId(entry.getKey(), 1);
                if (radioAll.isSelected() ||
                        (radioItems.isSelected() && !item.isHasPackage()) ||
                        (radioGroups.isSelected() && item.isHasPackage())) {
                    item.setMini_quantity(entry.getValue());
                    itemsModelObservableList.add(item);
                }
            }

            tableView.getItems().clear();
            tableView.getItems().setAll(FXCollections.observableArrayList(itemsModelObservableList));
            tableView.refresh();
        } catch (DaoException e) {
            Platform.runLater(() -> {
                log.error(e.getMessage(), e.getCause());
                tableView.getItems().clear();
                AllAlerts.alertError(e.getMessage());
            });
        }
    }

    private void applyRowColoringForBalance() {
        tableView.setRowFactory(itemsModelTableView -> {
            TableRow<ItemsModel> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    if (newValue.isHasPackage()) {
                        row.setStyle("-fx-background-color: rgba(243,253,163,0.62)");
                    }
                }
            });
            return row;
        });

    }
}
