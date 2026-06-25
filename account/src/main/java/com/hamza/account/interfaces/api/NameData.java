package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.SelPriceTypeModel;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.Callback;

import java.util.function.Function;

/**
 * @param <T> for Names (Customers or Suppliers)
 */
public interface NameData<T extends BaseNames> extends DataTable<T> {

    T objectT(String name, String tel, String address, String notes
            , double limit, double firstBalance, SelPriceTypeModel priceTypeModel, Area area);

    default Function<T, String> getName() {
        return BaseNames::getName;
    }

    default Function<T, Integer> getId() {
        return BaseNames::getId;
    }

    default int getId(T t) {
        return t.getId();
    }

    default String getName(T t) {
        return t.getName();
    }

    default String getTel(T t) {
        return t.getTel();
    }

    default String getAddress(T t) {
        return t.getAddress();
    }

    default String getNotes(T t) {
        return t.getNotes();
    }

    default double firstBalance(T t) {
        return t.getFirst_balance();
    }

    default void setId(T t, int i) {
        t.setId(i);
    }

    default TableColumn<T, String> addColumn(String name, Callback<TableColumn.CellDataFeatures<T, String>, ObservableValue<String>> cellData) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cellData);
        return column;
    }

    void addColumns(TableView<T> tableView);

    default Function<T, Double> getCreditLimit() {
        return t -> 0.0;
    }

    default String getPriceType(T t) {
        return "";
    }

    default Area getArea(T t) {
        return t.getArea();
    }

    default double limit(T t) {
        return 0.0;
    }

    default int priceId(T t) {
        return 0;
    }

    String getFrom();

    void actionColumnShow(T t, DaoFactory daoFactory) throws Exception;
}
