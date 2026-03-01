package com.hamza.account.controller.main;

import com.hamza.account.interfaces.spinner.DataBySpinner;
import com.hamza.account.model.domain.*;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

public class MainSplit {

    private DataBySpinner<Sales, Total_Sales, Customers, CustomerAccount> salesTotalSalesCustomersCustomerAccountDataBySpinner;
    private DataBySpinner<Purchase, Total_buy, Suppliers, SupplierAccount> purchaseTotalBuySuppliersSupplierAccountDataBySpinner;
    private ObservableList<XYChart.Series<String, Number>> seriesBuy;
    private ObservableList<XYChart.Series<String, Number>> seriesSales;
    private ObservableList<XYChart.Series<String, Number>> seriesItems;

    public MainSplit() {
//        try {
//            this.purchaseTotalBuySuppliersSupplierAccountDataBySpinner = new DataBySpinner<>(dataInterfacePurchase);
//            this.salesTotalSalesCustomersCustomerAccountDataBySpinner = new DataBySpinner<>(dataInterfaceSales);
//            seriesBuy = purchaseTotalBuySuppliersSupplierAccountDataBySpinner.chartObservableList(integer);
//            seriesSales = salesTotalSalesCustomersCustomerAccountDataBySpinner.chartObservableList(integer);
//            seriesItems = maxItems();
//        } catch (DaoException e) {
//            logException(e);
//        }

     /*  this.getPublisherSales().addObserver(message -> {
            seriesSales.setAll(salesTotalSalesCustomersCustomerAccountDataBySpinner.chartObservableList(integer));
            seriesItems.setAll(maxItems());
        });

        this.getPublisherBuy().addObserver(message -> seriesBuy.setAll(purchaseTotalBuySuppliersSupplierAccountDataBySpinner.chartObservableList(integer)));*/
    }

//    private ObservableList<XYChart.Series<String, Number>> maxItems() {
//        ObservableList<XYChart.Series<String, Number>> list = FXCollections.observableArrayList();
//        int i = 1;
//        for (ItemsModel itemsModel : itemsService.maxItemsSold()) {
//            XYChart.Series<String, Number> series5 = new XYChart.Series<>();
//            series5.setName(itemsModel.getNameItem());
//            series5.getData().add(new XYChart.Data<>(itemsModel.getNameItem(), itemsModel.sumSalesProperty().get()));
//            list.add(series5);
//        }
//        return list;
//    }

//    private void addChartAll() {
//        ChartDesign chartBuy = new ChartDesign(seriesBuy
//                , Setting_Language.MONTHS, Setting_Language.WORD_TOTAL
//                , Setting_Language.TOP_5_PURCHASING_SUPPLIERS_BY_YEAR + " " + integer
//                , LineChart::new);
////        splitPane.getItems().add(chartBuy.getPane());
//
//        ChartDesign chartSales = new ChartDesign(seriesSales
//                , Setting_Language.MONTHS, Setting_Language.WORD_TOTAL
//                , Setting_Language.TOP_5_Sales_customer_BY_YEAR + " " + integer
//                , LineChart::new);
////        splitPane.getItems().add(chartSales.getPane());
//
//
//        ChartDesign chartItems = new ChartDesign(seriesItems
//                , Setting_Language.WORD_ITEMS, Setting_Language.WORD_TOTAL
//                , "أكثر الاصناف مبيعا"
//                , BarChart::new);
//        AnchorPane pane = chartItems.getPane();
//        HBox.setHgrow(pane, Priority.SOMETIMES);
//    }
}
