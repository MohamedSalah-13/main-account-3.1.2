package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public abstract class BasePurchasesAndSales extends UnitExtends {

    private IntegerProperty id = new SimpleIntegerProperty();
    private IntegerProperty invoiceNumber = new SimpleIntegerProperty();
    private IntegerProperty numItem = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.QUANTITY)
    private DoubleProperty quantity = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.PRICE)
    private DoubleProperty price = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.TOTAL)
    private DoubleProperty total = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.DISCOUNT)
    private DoubleProperty discount = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.TOTAL_AFTER)
    private DoubleProperty total_after_discount = new SimpleDoubleProperty();

    // for profit for sales
//    @ColumnData(titleName = NamesTables.BUY_PRICE)
    private DoubleProperty buy_price = new SimpleDoubleProperty();
//    @ColumnData(titleName = NamesTables.TOTAL_BUY_PRICE)
    private DoubleProperty total_buy_price = new SimpleDoubleProperty();


    // for other class - purchase , purchaseRe , salesRe
    private double quantityByUnit;
    private LocalDate expiration_date;
    private ObjectProperty<ItemsModel> items = new SimpleObjectProperty<>();

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public int getInvoiceNumber() {
        return invoiceNumber.get();
    }

    public void setInvoiceNumber(int invoiceNumber) {
        this.invoiceNumber.set(invoiceNumber);
    }

    public IntegerProperty invoiceNumberProperty() {
        return invoiceNumber;
    }

    public int getNumItem() {
        return numItem.get();
    }

    public void setNumItem(int numItem) {
        this.numItem.set(numItem);
    }

    public IntegerProperty numItemProperty() {
        return numItem;
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

    public double getPrice() {
        return price.get();
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public double getDiscount() {
        return discount.get();
    }

    public void setDiscount(double discount) {
        this.discount.set(discount);
    }

    public DoubleProperty discountProperty() {
        return discount;
    }

    public double getTotal() {
        return total.get();
    }

    public void setTotal(double total) {
        this.total.set(total);
    }

    public DoubleProperty totalProperty() {
        return total;
    }

    public double getTotal_after_discount() {
        return total_after_discount.get();
    }

    public void setTotal_after_discount(double total_after_discount) {
        this.total_after_discount.set(total_after_discount);
    }

    public DoubleProperty total_after_discountProperty() {
        return total_after_discount;
    }

    public ItemsModel getItems() {
        return items.get();
    }

    public void setItems(ItemsModel items) {
        this.items.set(items);
    }

    public ObjectProperty<ItemsModel> itemsProperty() {
        return items;
    }

    public double getBuy_price() {
        return buy_price.get();
    }

    public void setBuy_price(double buy_price) {
        this.buy_price.set(buy_price);
    }

    public DoubleProperty buy_priceProperty() {
        return buy_price;
    }

    public double getTotal_buy_price() {
        return total_buy_price.get();
    }

    public void setTotal_buy_price(double total_buy_price) {
        this.total_buy_price.set(total_buy_price);
    }

    public DoubleProperty total_buy_priceProperty() {
        return total_buy_price;
    }

}


