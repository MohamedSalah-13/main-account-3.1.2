package com.hamza.account.model.domain;

import com.hamza.account.model.base.UnitExtends;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Items_Stock_Model extends UnitExtends {

    private Integer id;
    private ItemsModel itemsModel;
    private Stock stock;
    private Double firstBalance;

    private DoubleProperty quantityToConvert = new SimpleDoubleProperty(); // this use when convert items by stocks

    public Items_Stock_Model(int itemId, int stockId, double firstBalance) {
        this.itemsModel = new ItemsModel(itemId);
        this.stock = new Stock(stockId);
        this.firstBalance = firstBalance;
    }

    public double getQuantityToConvert() {
        return quantityToConvert.get();
    }

    public void setQuantityToConvert(double quantityToConvert) {
        this.quantityToConvert.set(quantityToConvert);
    }

    public DoubleProperty quantityToConvertProperty() {
        return quantityToConvert;
    }
}
