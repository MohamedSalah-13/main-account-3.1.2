package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.ItemsModel;
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
    private int invoiceNumber;
    private int numItem;
    private DoubleProperty quantity = new SimpleDoubleProperty();
    private DoubleProperty price = new SimpleDoubleProperty();
    private DoubleProperty total = new SimpleDoubleProperty();
    private DoubleProperty discount = new SimpleDoubleProperty();
    private DoubleProperty total_after_discount = new SimpleDoubleProperty();

    // for profit for sales
    private double buy_price;
    private double total_buy_price;


    // for other class - purchase , purchaseRe , salesRe
    private double quantityByUnit;
    private LocalDate expiration_date;
    private ObjectProperty<ItemsModel> items = new SimpleObjectProperty<>();

    /**
     * The sold/purchased line this row reverses - {@code sales.id}/{@code purchase.id},
     * not an item column. Meaningless outside {@code Sales_Return}/{@code Purchase_Return}
     * and {@code 0} by default, which is every row before {@code V16__return_source.sql}
     * and every row of a return entered without picking a source invoice. Persisted to
     * {@code sales_re.source_line_id}/{@code purchase_re.source_line_id}; read by
     * {@code ReturnCostResolver} to recover the original line's cost before it is lost
     * to today's item price.
     */
    private int sourceLineId;

    /**
     * The unit price and the line discount as they were typed, in the document's currency, beside
     * the base figures in {@link #price} and {@link #discount} (V83, docs/currency-plan.md §15 ق-د٣).
     * Null on a line of a document written in the base - every line before V83, and every line of a
     * party in the base - which is why these are plain values and not properties: nothing binds to
     * them, the save writes them and the reopen and the paper read them.
     */
    private java.math.BigDecimal priceForeign;
    private java.math.BigDecimal discountForeign;

    public java.math.BigDecimal getPriceForeign() {
        return priceForeign;
    }

    public void setPriceForeign(java.math.BigDecimal priceForeign) {
        this.priceForeign = priceForeign;
    }

    public java.math.BigDecimal getDiscountForeign() {
        return discountForeign;
    }

    public void setDiscountForeign(java.math.BigDecimal discountForeign) {
        this.discountForeign = discountForeign;
    }

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public int getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(int sourceLineId) {
        this.sourceLineId = sourceLineId;
    }

    public int getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(int invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public int getNumItem() {
        return numItem;
    }

    public void setNumItem(int numItem) {
        this.numItem = numItem;
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
        return buy_price;
    }

    public void setBuy_price(double buy_price) {
        this.buy_price = buy_price;
    }

    public double getTotal_buy_price() {
        return total_buy_price;
    }

    public void setTotal_buy_price(double total_buy_price) {
        this.total_buy_price = total_buy_price;
    }

}


