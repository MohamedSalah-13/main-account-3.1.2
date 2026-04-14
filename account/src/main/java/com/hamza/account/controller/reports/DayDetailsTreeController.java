package com.hamza.account.controller.reports;

import com.hamza.account.config.FxmlConstants;
import com.hamza.account.controller.model.DataTable;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.table.Column;
import com.hamza.controlsfx.table.TreeTable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import javafx.util.Callback;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Log4j2
public class DayDetailsTreeController implements AppSettingInterface {

    private final String date;
    private final DaoFactory daoFactory;
    private final DoubleProperty sumSales = new SimpleDoubleProperty(0);
    private final DoubleProperty sumPaid = new SimpleDoubleProperty(0);
    @FXML
    private TreeTableView<DataTable> treeView;
    @FXML
    private Text textDate;

    public DayDetailsTreeController(String date, DaoFactory daoFactory) {
        this.date = date;
        this.daoFactory = daoFactory;
    }

    @FXML
    public void initialize() {
        textDate.setText(date);
        TreeTable.createTable(treeView, COLUMN_LIST());

        int index = treeView.getColumns().size();
        addColumnToTable(Setting_Language.PURCHASE, index, f -> f.getValue().getValue().purchaseProperty().asString());
        index++;
        addColumnToTable(Setting_Language.WORD_PAID, index, f -> f.getValue().getValue().paidPurchaseProperty().asString());
        index++;
        addColumnToTable(Setting_Language.SALES, index, f -> f.getValue().getValue().salesProperty().asString());
        index++;
        addColumnToTable(Setting_Language.WORD_PAID, index, f -> f.getValue().getValue().paidSalesProperty().asString());

        // data main table
        DataTable dataTable = new DataTable(0, Setting_Language.TOTAL, 0, 0, 0, 0);
        dataTable.salesProperty().bind(sumSalesProperty());
        dataTable.paidSalesProperty().bind(sumPaidProperty());


        TreeItem<DataTable> rootItem = new TreeItem<>(dataTable);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        // change size of table column
        treeView.getColumns().get(0).setPrefWidth(100);
        treeView.getColumns().get(1).setPrefWidth(250);


        // add treeItem to root
        List<TreeItem<DataTable>> addTreeMain = new ArrayList<>();

        // add data to tree
        try {
            addTreeMain.add(treeItemSales());
            addTreeMain.add(treeItemPaid());
        } catch (DaoException e) {
            log.error(e.getMessage());
        }

        treeView.getRoot().getChildren().addAll(addTreeMain);

        // change tree item color
        changeRowColor();
    }

    private void changeRowColor() {
        treeView.setRowFactory(sTableView -> {
            TreeTableRow<DataTable> row = new TreeTableRow<>();
            row.itemProperty().addListener((observableValue, s, t1) -> {
                if (t1 != null) {
                    switch (t1.getCode()) {
                        case 0:
                            row.setStyle("""
                                    -fx-background-color: #4380bb;
                                    -fx-border-color: #d5ba50;
                                    -fx-border-width: 2 0 0 0;
                                    -fx-font-weight: bold;
                                    -fx-text-background-color: #ffffff;
                                    -fx-font-size: 15px;""");
                        case 1:
                            row.pseudoClassStateChanged(PseudoClass.getPseudoClass("aquaRow"), t1.getCode() == 1);
                        case 2:
                            row.pseudoClassStateChanged(PseudoClass.getPseudoClass("row2"), t1.getCode() == 2);
                    }


                }
                treeView.refresh();
            });

            return row;
        });
    }

    private void addColumnToTable(String nameColumn, int index, Callback<TreeTableColumn.CellDataFeatures<DataTable, String>, ObservableValue<String>> cellDataFeaturesObservableValueCallback) {
        // add column sales to table
        TreeTableColumn<DataTable, String> column = new TreeTableColumn<>(nameColumn);
        column.setCellValueFactory(cellDataFeaturesObservableValueCallback);
        treeView.getColumns().add(index, column);

    }

    private List<Column<?>> COLUMN_LIST() {
        return new ArrayList<>(Arrays.asList(new Column<>(Integer.class, "code", Setting_Language.WORD_CODE)
                , new Column<>(String.class, "nameData", Setting_Language.WORD_NAME)));
    }

    /*--------------------------------- Sales ---------------------------------*/
    private TreeItem<DataTable> treeItemSales() throws DaoException {
        List<Total_Sales> totalSalesList = daoFactory.totalsSalesDao().loadAll()
                .stream()
                .filter(totalSales1 -> totalSales1.getDate().equals(date))
                .toList();

        // make main tree item
        // sum totals in day
        double sum = totalSalesList.stream().mapToDouble(Total_Sales::getPaid).sum();

        sumSalesProperty().setValue(totalSalesList.stream().mapToDouble(Total_Sales::getTotal_after_discount).sum());
        sumPaidProperty().set(sum);

        TreeItem<DataTable> sales = new TreeItem<>(new DataTable(1, Setting_Language.SALES, 0, sumSales.get(), 0, sum));

        for (Total_Sales totalSales : totalSalesList) {
            // add items to tree
            sales.getChildren().add(new TreeItem<>(new DataTable(totalSales.getId(), totalSales.getCustomers().getName(), 0, totalSales.getTotal_after_discount(), 0, totalSales.getPaid())));
        }
        return sales;
    }

    /*--------------------------------- Paid ---------------------------------*/
    private TreeItem<DataTable> treeItemPaid() throws DaoException {
        List<CustomerAccount> customerAccountList = daoFactory.customerAccountDao().loadAll()
                .stream()
                .filter(account -> account.getDate().equals(date))
                .filter(customerAccount -> customerAccount.getId() != 0)//remove all invoice from sales
                .toList();

        // make main tree item
        // sum paid in day
        double sum = customerAccountList.stream().mapToDouble(CustomerAccount::getPaid).sum();
        TreeItem<DataTable> paid = new TreeItem<>(new DataTable(2, Setting_Language.WORD_PAID, 0, 0, 0, sum));
        sumPaidProperty().set(sumPaid.get() + sum);

        for (CustomerAccount customerAccount : customerAccountList) {
            paid.getChildren().add(new TreeItem<>(new DataTable(customerAccount.getId(), customerAccount.getCustomers().getName(), 0, 0, 0, customerAccount.getPaid())));
        }
        return paid;
    }

    public DoubleProperty sumSalesProperty() {
        return sumSales;
    }

    public DoubleProperty sumPaidProperty() {
        return sumPaid;
    }

    @Override
    public Pane pane() throws IOException {
        FXMLLoader fxmlLoader = new FxmlConstants().dayDetails2;
        fxmlLoader.setController(this);
        return fxmlLoader.load();
    }

    @Override
    public String title() {
        return Setting_Language.DETAILS;
    }

    @Override
    public boolean resize() {
        return true;
    }
}

