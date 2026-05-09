package com.hamza.account.interfaces.names;

import com.hamza.account.interfaces.CustomerPurchaseInterface;
import com.hamza.account.interfaces.api.NameData;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.SelPriceTypeModel;
import com.hamza.account.view.CustomerPurchasedItemsApplication;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.Setting_Language;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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

        tableView.getColumns().add(tableColumnSelPriceType);
        tableView.getColumns().add(tableColumnArea);
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

    @Override
    public void actionColumnShow(Customers customers, DaoFactory daoFactory) throws Exception {
        var app = new CustomerPurchasedItemsApplication(daoFactory
                , customers.getId(), customers.getName(), new CustomerPurchaseInterface() {
            @Override
            public List<CustomerPurchasedItem> getPurchasedItemsByCustomerId(int customerId) throws DaoException {
                return daoFactory.customerPurchasedItemDao().findByCustomerId(customerId);
            }

            @Override
            public String title() {
                return "الأصناف المشتراة من العميل";
            }
        });
        app.start(new javafx.stage.Stage());
    }

}
