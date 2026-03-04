package com.hamza.account.controller.invoice;

import com.hamza.account.config.PropertiesName;
import com.hamza.account.controller.main.DataPublisher;
import com.hamza.account.controller.main.LoadOtherData;
import com.hamza.account.controller.model_print.ModelPrintInvoice;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.PurchaseSalesInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.openFxml.FxmlPath;
import com.hamza.account.openFxml.OpenFxmlApplication;
import com.hamza.account.view.LogApplication;
import com.hamza.account.view.barcode.PrintBarcodeApp;
import com.hamza.account.view.barcode.PrintBarcodeModel;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.TableColumnAnnotation;
import com.hamza.controlsfx.table.colorRow.RowColor;
import com.hamza.controlsfx.table.colorRow.RowColorInterface;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.ToDoubleFunction;

import static com.hamza.controlsfx.table.columnEdit.ColumnSetting.addColumn;
import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@FxmlPath(pathFile = "invoice/showInv-view.fxml")
public class ShowInvoiceController<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        extends LoadOtherData<T1, T2, T3, T4> implements Initializable, AppSettingInterface {

    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private final PurchaseSalesInterface t1PurchaseSalesInterface;
    private final String name;
    private final int invNum;
    @FXML
    private TableView<BasePurchasesAndSales> tableView;
    @FXML
    private Label labelCount, labelCountItems, labelTotals, labelDiscount, labelAfterDiscount, labelPaid, labelRest, labelCode, labelName, labelDate, labelStock;
    @FXML
    private Label labelTotalAfterDiscount, labelOtherDiscount, labelTotal, labelType, labelProfit, labelTotalCost;
    @FXML
    private Text textCount, textQuantity, textTotal, textDiscount, textAfterDiscount, textPaid, textRest, txtDate, txtCode, txtName, txtStock, textType,
            textInvoiceTotal, textInvoiceDiscount, textInvoiceAfterDiscount, textInvoiceProfit, textTotalProfit;
    @FXML
    private VBox root;
    @FXML
    private Button btnPrint, btnPrintBarcode;
    private List<T1> list;
    private T2 tObject;
    private String date_insert;

    public ShowInvoiceController(DataInterface<T1, T2, T3, T4> dataInterface
            , DaoFactory daoFactory, DataPublisher dataPublisher
            , int num, String name) throws Exception {
        super(dataInterface, daoFactory, dataPublisher);
        this.invNum = num;
        this.name = name;
        this.dataInterface = dataInterface;
        this.t1PurchaseSalesInterface = dataInterface.purchaseSalesInterface();
    }


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        loadData();
        getTable();
        otherSetting();
        rowColorForSearchItemsName();
    }

    private void loadData() {
        try {
            tObject = dataInterface.totalsAndPurchaseList().totalDao().getDataById(invNum);
            list = dataInterface.listForAllPurchase(invNum);
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            AllAlerts.showExceptionDialog(e);
        }
    }

    private void getTable() {
        new TableColumnAnnotation().getTable(tableView, BasePurchasesAndSales.class);
        tableView.setItems(FXCollections.observableArrayList(list));
        tableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        addColumnTable();
    }

    private void otherSetting() {
        labelCode.setText(Setting_Language.WORD_NUM_INV);
        labelName.setText(Setting_Language.WORD_NAME);
        labelDate.setText(Setting_Language.WORD_DATE);
        labelStock.setText(Setting_Language.WORD_STOCK);

        labelCount.setText(Setting_Language.WORD_COUNT);
        labelCountItems.setText(Setting_Language.COUNT_ITEMS);
        labelTotals.setText(Setting_Language.WORD_TOTAL);
        labelDiscount.setText(Setting_Language.WORD_DISCOUNT);
        labelAfterDiscount.setText(Setting_Language.WORD_AFTER_DIS);
        labelPaid.setText(Setting_Language.WORD_PAID);
        labelRest.setText(Setting_Language.WORD_REST);

        labelProfit.setText(Setting_Language.GROSS_PROFIT);
        labelTotalCost.setText(Setting_Language.TOTAL_COST);

        labelType.setText(Setting_Language.WORD_TYPE);
        labelTotal.setText(Setting_Language.WORD_TOTAL);
        labelOtherDiscount.setText(Setting_Language.WORD_DISCOUNT);
        labelTotalAfterDiscount.setText(Setting_Language.WORD_AFTER_DIS);
        btnPrint.setText(Setting_Language.WORD_PRINT);
        btnPrintBarcode.setText(Setting_Language.PRINT_BARCODE);

        // show profit
//        var b = dataInterface.designInterface().showDataForCustomer();
        var b = LogApplication.usersVo.getId() == 1;
        labelProfit.setVisible(b);
        labelTotalCost.setVisible(b);
        textInvoiceProfit.setVisible(b);
        textTotalProfit.setVisible(b);

        // other data
        HashMap<String, Object> hashMap = ShowInvoiceDetails.invoiceDetails(dataInterface, tObject);
        txtCode.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.ID)));
        txtName.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.NAME)));
        txtDate.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.DATE)));
        txtStock.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.STOCK)));
        date_insert = hashMap.get(ShowInvoiceNameData.DATE_INSERT).toString();

        double invoiceTotal = (double) hashMap.get(ShowInvoiceNameData.TOTAL);
        double invoiceDiscount = (double) hashMap.get(ShowInvoiceNameData.DISCOUNT);
        textType.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.TYPE)));
        textInvoiceTotal.setText(String.valueOf(invoiceTotal));
        textInvoiceDiscount.setText(String.valueOf(invoiceDiscount));
        textInvoiceAfterDiscount.setText(String.valueOf(invoiceTotal - invoiceDiscount));

        ToDoubleFunction<BasePurchasesAndSales> totalFunction = BasePurchasesAndSales::getTotal;
        ToDoubleFunction<BasePurchasesAndSales> quantityFunction = BasePurchasesAndSales::getQuantity;
        ToDoubleFunction<BasePurchasesAndSales> discountFunction = BasePurchasesAndSales::getDiscount;
        ToDoubleFunction<BasePurchasesAndSales> totalAfterDiscountFunction = BasePurchasesAndSales::getTotal_after_discount;
        ToDoubleFunction<BasePurchasesAndSales> totalProfitAfterDiscountInItem = t1PurchaseSalesInterface.getTotalBuy();

        double sumQuantity = tableView.getItems().stream().mapToDouble(quantityFunction).sum();
        double sumTotal = tableView.getItems().stream().mapToDouble(totalFunction).sum();
        double sumDiscount = tableView.getItems().stream().mapToDouble(discountFunction).sum();
        double sumAfterDiscount = tableView.getItems().stream().mapToDouble(totalAfterDiscountFunction).sum();
        double sumTotalCost = tableView.getItems().stream().mapToDouble(totalProfitAfterDiscountInItem).sum();

        textCount.setText(String.valueOf(tableView.getItems().size()));
        textQuantity.setText(String.valueOf(roundToTwoDecimalPlaces(sumQuantity)));
        textTotal.setText(String.valueOf(roundToTwoDecimalPlaces(sumTotal)));
        textDiscount.setText(String.valueOf(roundToTwoDecimalPlaces(sumDiscount)));
        textAfterDiscount.setText(String.valueOf(roundToTwoDecimalPlaces(sumAfterDiscount)));
        textTotalProfit.setText(String.valueOf(roundToTwoDecimalPlaces(sumTotalCost)));

        textPaid.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.PAID)));
        textRest.setText(String.valueOf(hashMap.get(ShowInvoiceNameData.REST)));

        // GROSS PROFIT
        textInvoiceProfit.setText(String.valueOf(roundToTwoDecimalPlaces(sumAfterDiscount - sumTotalCost - invoiceDiscount)));

        btnPrint.setOnAction(actionEvent -> printData());
        btnPrintBarcode.setOnAction(actionEvent -> printBarcode());
    }

    private void printData() {
        List<ModelPrintInvoice> modelPrintInvoices = new ArrayList<>();
        for (T1 t11 : list) {
            ItemsModel itemsModel = t1PurchaseSalesInterface.getItems(t11);
            double price = t1PurchaseSalesInterface.getPrice(t11);
            double quantity = t1PurchaseSalesInterface.getQuantity(t11);
            double total = t1PurchaseSalesInterface.getTotal(t11);
            String unitName = t1PurchaseSalesInterface.getUnitsType(t11).getUnit_name();
            double discount = t1PurchaseSalesInterface.getDiscount(t11);
            ModelPrintInvoice modelPrintInvoice = new ModelPrintInvoice(itemsModel.getNameItem(), itemsModel.getBarcode(), unitName, price
                    , quantity, total, discount, total - discount);
            modelPrintInvoices.add(modelPrintInvoice);
        }
        if (PropertiesName.getPrintPaperReceiptAccount()) {
            printReports.printReceiptInvoice(modelPrintInvoices, txtName.getText(), invNum
                    , Double.parseDouble(textInvoiceDiscount.getText()), date_insert, txtDate.getText(), 0);
        } else
            printReports.printInvoice(modelPrintInvoices, ShowInvoiceDetails.invoiceDetails(dataInterface, tObject), dataInterface.designInterface().nameTextOfInvoice());
    }

    private void rowColorForSearchItemsName() {
        if (name != null) {
            TableColumn<T1, String> itemsTableColumn = (TableColumn<T1, String>) tableView.getColumns().get(1);
            new RowColor().customiseRowByCell(itemsTableColumn, new RowColorInterface<>() {
                @Override
                public boolean checkRow(TableCell<T1, String> tsTableCell) {
                    if (tsTableCell != null) {
                        return t1PurchaseSalesInterface.getItems(tsTableCell.getTableRow().getItem()).getNameItem().equals(name);
                    }
                    return false;
                }
            });
        }
    }

    private void printBarcode() {
        ObservableList<PrintBarcodeModel> observableList = FXCollections.observableArrayList();
        if (list.isEmpty()) {
            AllAlerts.alertError(Error_Text_Show.PLEASE_INSERT_ALL_DATA);
            return;
        }

        for (BasePurchasesAndSales t11 : list) {
            var itemsModel = t1PurchaseSalesInterface.getItems(t11);
            observableList.add(new PrintBarcodeModel(itemsModel.getBarcode(), itemsModel.getNameItem()
                    , itemsModel.getSelPrice1()));
        }

        try {
            new PrintBarcodeApp(observableList);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }
    }

    private void addColumnTable() {
        Callback<TableColumn.CellDataFeatures<BasePurchasesAndSales, String>, ObservableValue<String>> cellCode = f -> f.getValue().getItems().idProperty().asString();
        addColumn(tableView, Setting_Language.WORD_CODE, 0, cellCode);
//                // add name from items
        Callback<TableColumn.CellDataFeatures<BasePurchasesAndSales, String>, ObservableValue<String>> cellName = f -> f.getValue().getItems().nameItemProperty();
        addColumn(tableView, Setting_Language.WORD_NAME, 1, cellName);

        Callback<TableColumn.CellDataFeatures<BasePurchasesAndSales, String>, ObservableValue<String>> cellUNit = f -> f.getValue().getUnitsType().unit_nameProperty();
        addColumn(tableView, Setting_Language.WORD_TYPE, 2, cellUNit);

        // remove column if not admin
        if (!dataInterface.designInterface().showDataForCustomer())
            for (int i = 0; i < 2; i++) {
                tableView.getColumns().remove(tableView.getColumns().size() - 2);
            }

    }


    @Override
    public @NotNull Pane pane() throws IOException {
        String style = dataInterface.designInterface().styleSheet();
        var pane = new OpenFxmlApplication(this).getPane();
        pane.getStylesheets().add(style);
        return pane;
    }

    @Override
    public String title() {
        return dataInterface.designInterface().nameTextOfInvoice();
    }

    @Override
    public boolean resize() {
        return true;
    }
}
