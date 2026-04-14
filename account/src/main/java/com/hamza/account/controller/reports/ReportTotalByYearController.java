package com.hamza.account.controller.reports;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.controller.model.TableDataReports;
import com.hamza.account.controller.others.ServiceData;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.table.TableSetting;
import com.hamza.account.type.MonthsEnum;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.util.ImageChoose;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import lombok.extern.log4j.Log4j2;
import org.controlsfx.control.CheckComboBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Log4j2
@FxmlPath(pathFile = "reports/total-year-profit.fxml")
public class ReportTotalByYearController extends ServiceData {

    private final List<TableDataReports> allData = new ArrayList<>();
    @FXML
    private CheckComboBox<String> checkComboBox;
    @FXML
    private TableView<TableDataReports> tableView;
    @FXML
    private ComboBox<Integer> comboYear;
    @FXML
    private Button searchButton, btnPrint;

    public ReportTotalByYearController(DaoFactory daoFactory) throws Exception {
        super(daoFactory);
    }

    @FXML
    public void initialize() {
        new TableColumnAnnotation().getTable(tableView, TableDataReports.class);
        TableSetting.tableMenuSetting(getClass(), tableView);

        checkComboBox.getItems().setAll(Arrays.stream(MonthsEnum.values()).map(MonthsEnum::getArabicName).toList());
        checkComboBox.getCheckModel().checkAll();
        searchButton.setText(Setting_Language.WORD_SEARCH);
        btnPrint.setText(Setting_Language.WORD_PRINT);

        var imageSetting = new Image_Setting();
        searchButton.setGraphic(ImageChoose.createIcon(imageSetting.search));
        btnPrint.setGraphic(ImageChoose.createIcon(imageSetting.print));

        // get all years from purchase and sales without duplicate
        //TODO 11/16/2025 9:42 AM Mohamed: get all years from purchase and sales without duplicate
//        var listYear = totalBuyService.getListYear();
        comboYear.getItems().setAll(2024, 2025);
        comboYear.getSelectionModel().selectFirst();

        btnPrint.setOnAction(event -> printTable());
        searchButton.setOnAction(event -> {
            try {
                searchAction();
            } catch (DaoException e) {
                log.error(e.getMessage(), e);
                AllAlerts.alertError(e.getMessage());
            }
        });
        checkComboBox.getCheckModel().getCheckedItems().addListener((javafx.collections.ListChangeListener<String>) c -> filterTable());

        tableView.getColumns().forEach(column ->
                column.setStyle("-fx-border-color: #135A8DFF; -fx-border-width: 0 0 0 .5;")
        );

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(TableDataReports item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    if (item.getName().equals(Setting_Language.WORD_TOTAL)) {
                        setStyle("-fx-background-color: #cccc69; -fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 14px;");
                    }
                }
            }
        });

    }

    private void searchAction() throws DaoException {
        if (comboYear.getValue() == null) {
            AllAlerts.alertError("من فضلك حدد السنة");
            return;
        }

        allData.clear();
        tableView.getItems().clear();
        var totalPurchase = totalBuyService.getTotalBuyByYear(comboYear.getValue());
        var totalSales = totalSalesService.getTotalSalesByYear(comboYear.getValue());
        var totalPurchaseRe = totalBuyReturnService.getTotalBuyByYear(comboYear.getValue());
        var totalSalesRe = totalSalesReturnService.getTotalSalesByYear(comboYear.getValue());


        for (var i = 0; i < MonthsEnum.values().length; i++) {
            var value = MonthsEnum.values()[i];
            var sumPurchase = totalPurchase.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getTotal).sum();
            var sumSales = totalSales.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getTotal).sum();
            var sumPurchaseRe = totalPurchaseRe.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getTotal).sum();
            var sumSalesRe = totalSalesRe.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getTotal).sum();
            // discount
            var sumPurchaseDiscount = totalPurchase.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getDiscount).sum();
            var sumSalesDiscount = totalSales.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getDiscount).sum();
            var sumPurchaseReDiscount = totalPurchaseRe.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getDiscount).sum();
            var sumSalesReDiscount = totalSalesRe.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(BaseTotals::getDiscount).sum();

            // الربح بعد خصم المرتجعات
            var sumSalesProfit = totalSales.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(Total_Sales::getTotal_profit).sum();
            var sumSalesReProfit = totalSalesRe.stream().filter(total_sales -> LocalDate.parse(total_sales.getDate()).getMonth().getValue() == value.getNumber()).mapToDouble(Total_Sales_Re::getTotal_profit).sum();

            String profit = String.format("%.2f", (sumSalesProfit * 100) / sumSales);
            var build = TableDataReports.builder()
                    .name(value.getArabicName())
                    .purchase(sumPurchase)
                    .discountPurchase(sumPurchaseDiscount)
                    .sales(sumSales)
                    .discountSales(sumSalesDiscount)
                    .purchaseRe(sumPurchaseRe)
                    .discountPurchaseRe(sumPurchaseReDiscount)
                    .salesRe(sumSalesRe)
                    .discountSalesRe(sumSalesReDiscount)
                    .profit(sumSalesProfit)
                    .profitPercent(sumSalesProfit > 0 ? profit + " %" : "0 %")
                    .build();
            allData.add(build);
        }
        filterTable();
    }


    private void filterTable() {
        if (allData.isEmpty()) return;
        tableView.getItems().clear();
        var selectedMonths = checkComboBox.getCheckModel().getCheckedItems();
        var filteredData = allData.stream()
                .filter(data -> selectedMonths.contains(data.getName()))
                .toList();
        tableView.getItems().addAll(filteredData);

        var sumPurchase = filteredData.stream().mapToDouble(TableDataReports::getPurchase).sum();
        var sumPurchaseDiscount = filteredData.stream().mapToDouble(TableDataReports::getDiscountPurchase).sum();

        var sumSales = filteredData.stream().mapToDouble(TableDataReports::getSales).sum();
        var sumSalesDiscount = filteredData.stream().mapToDouble(TableDataReports::getDiscountSales).sum();

        var sumPurchaseRe = filteredData.stream().mapToDouble(TableDataReports::getPurchaseRe).sum();
        var sumPurchaseReDiscount = filteredData.stream().mapToDouble(TableDataReports::getDiscountPurchaseRe).sum();

        var sumSalesRe = filteredData.stream().mapToDouble(TableDataReports::getSalesRe).sum();
        var sumSalesReDiscount = filteredData.stream().mapToDouble(TableDataReports::getDiscountSalesRe).sum();

        var sumProfit = filteredData.stream().mapToDouble(TableDataReports::getProfit).sum();

        tableView.getItems().add(TableDataReports.builder()
                .name(Setting_Language.WORD_TOTAL)
                .purchase(sumPurchase)
                .discountPurchase(sumPurchaseDiscount)
                .sales(sumSales)
                .discountSales(sumSalesDiscount)
                .purchaseRe(sumPurchaseRe)
                .discountPurchaseRe(sumPurchaseReDiscount)
                .salesRe(sumSalesRe)
                .discountSalesRe(sumSalesReDiscount)
                .profit(sumProfit)
                .build());
    }

    private void printTable() {
        //TODO 11/16/2025 9:15 AM Mohamed: add print
        AllAlerts.alertError("لم يتم عمل التقرير");
    }

}

