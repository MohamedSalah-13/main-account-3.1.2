package com.hamza.account.interfaces.names;

import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.view.DownLoadApplication;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class CustomerName implements NameData<Customers> {


    @Override
    public @NotNull Class<? super Customers> classForColumn() {
        return Customers.class;
    }

    @Override
    public Customers objectT(String name, String tel, String address, String notes, double limit, double firstBalance
            , SelPriceTypeModel priceTypeModel, Area area) {
        Customers customers = new Customers();
        customers.setName(name);
        customers.setTel(tel);
        customers.setAddress(address);
        customers.setNotes(notes);
        customers.setCredit_limit(limit);
        customers.setFirst_balance(firstBalance);
        customers.setSelPriceObject(priceTypeModel);
        customers.setArea(area);
        return customers;
    }

    @Override
    public void addColumns(TableView<Customers> tableView) {
        TableColumn<Customers, String> tableColumnSelPriceType = addColumn(Setting_Language.WORD_SEL_PRICE
                , f -> f.getValue().getSelPriceObject().nameProperty());

        TableColumn<Customers, String> tableColumnArea = addColumn(Setting_Language.AREA
                , f -> new SimpleStringProperty(f.getValue().areaProperty().get().getArea_name()));

        TableColumn<Customers, String> printColumn = new TableColumn<>("Show");
        printColumn.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button(Setting_Language.WORD_SHOW);

            {
                btn.setOnAction(e -> {
                    Customers customer = getTableView().getItems().get(getIndex());
                    // Print logic here
//                    new Print_Reports().printReceiptNames(customer.getAddress(), customer.getTel(), customer.getName());

                    try {
                        var app = new com.hamza.account.view.CustomerPurchasedItemsApplication(DownLoadApplication.getDaoFactory()
                                , customer.getId(), customer.getName());
                        app.start(new javafx.stage.Stage());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tableView.getColumns().add(tableColumnSelPriceType);
        tableView.getColumns().add(tableColumnArea);
        tableView.getColumns().add(printColumn);

    }

    @Override
    public Function<Customers, Double> getCreditLimit() {
        return Customers::getCredit_limit;
    }

    @Override
    public String getPriceType(Customers customers) {
        return customers.getSelPriceObject().getName();
    }

    @Override
    public double limit(Customers customers) {
        return customers.getCredit_limit();
    }

    @Override
    public int priceId(Customers customers) {
        return customers.getSelPriceObject().getId();
    }

    @Override
    public String getFrom() {
        return "customer";
    }

}
