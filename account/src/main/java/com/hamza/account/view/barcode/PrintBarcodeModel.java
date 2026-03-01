package com.hamza.account.view.barcode;

import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

@lombok.Data
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class PrintBarcodeModel {

    @ColumnData(titleName = "باركود")
    private String barcode;
    @ColumnData(titleName = "اسم الصنف")
    private String name;
    @ColumnData(titleName = "السعر")
    private DoubleProperty price = new SimpleDoubleProperty();
    @ColumnData(titleName = "الكمية")
    private IntegerProperty quantity = new SimpleIntegerProperty();
    private String buttonColumnName;

    public PrintBarcodeModel(String barcode, String name, double price) {
        this.barcode = barcode;
        this.name = name;
        this.price = new SimpleDoubleProperty(price);
        this.quantity = new SimpleIntegerProperty(1);
    }

    public double getPrice() {
        return price.get();
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public int getQuantity() {
        return quantity.get();
    }

    public void setQuantity(int quantity) {
        this.quantity.set(quantity);
    }

    public IntegerProperty quantityProperty() {
        return quantity;
    }

}
