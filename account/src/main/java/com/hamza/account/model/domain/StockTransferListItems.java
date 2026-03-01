package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferListItems extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    private int stock_transfer_id;
    private ObjectProperty<ItemsModel> item = new SimpleObjectProperty<>();
    @ColumnData(titleName = NamesTables.QUANTITY)
    private DoubleProperty quantity = new SimpleDoubleProperty();

    public ItemsModel getItem() {
        return item.get();
    }

    public void setItem(ItemsModel item) {
        this.item.set(item);
    }

    public ObjectProperty<ItemsModel> itemProperty() {
        return item;
    }

    public double getQuantity() {
        return quantity.get();
    }

    public void setQuantity(double quantity) {
        this.quantity.set(quantity);
    }

    public DoubleProperty quantityProperty() {
        return quantity;
    }
}
