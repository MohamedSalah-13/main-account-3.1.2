package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseEntity;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Items_Package extends BaseEntity {

    private int package_id;
    private int items_id;
    @ColumnData(titleName = NamesTables.QUANTITY)
    private DoubleProperty quantity = new SimpleDoubleProperty();
    private ItemsModel itemsModel;

    public Items_Package(int id, int package_id, int items_id, double quantity) {
        this(package_id, items_id, quantity);
        setId(id);
    }

    public Items_Package(int package_id, int items_id, double quantity) {
        this.package_id = package_id;
        this.items_id = items_id;
        this.quantity.set(quantity);
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
