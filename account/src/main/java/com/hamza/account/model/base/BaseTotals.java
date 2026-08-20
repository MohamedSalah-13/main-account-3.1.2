package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceStatus;
import com.hamza.account.type.InvoiceType;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseTotals extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty date = new SimpleStringProperty();
    private DoubleProperty total = new SimpleDoubleProperty(0.0);
    private DoubleProperty discount = new SimpleDoubleProperty(0.0);
    private DoubleProperty total_after_discount = new SimpleDoubleProperty(0.0);
    private DoubleProperty paid = new SimpleDoubleProperty(0.0);
    private double rest;
    private String notes;

    private Stock stockData;
    private Treasury treasuryModel;

    private double otherPaid;
    private double amountAfterOtherPaid;
    private InvoiceType invoiceType;
    private InvoiceStatus invoice_status;
    private DiscountType discountType;

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public String getDate() {
        return date.get();
    }

    public void setDate(String date) {
        this.date.set(date);
    }

    public StringProperty dateProperty() {
        return date;
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

    public double getDiscount() {
        return discount.get();
    }

    public void setDiscount(double discount) {
        this.discount.set(discount);
    }

    public DoubleProperty discountProperty() {
        return discount;
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

    public double getPaid() {
        return paid.get();
    }

    public void setPaid(double paid) {
        this.paid.set(paid);
    }

    public DoubleProperty paidProperty() {
        return paid;
    }

    public double getRest() {
        return rest;
    }

    public void setRest(double rest) {
        this.rest = rest;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Stock getStockData() {
        return stockData;
    }

    public void setStockData(Stock stockData) {
        this.stockData = stockData;
    }

    public Treasury getTreasuryModel() {
        return treasuryModel;
    }

    public void setTreasuryModel(Treasury treasury) {
        this.treasuryModel = treasury;
    }

    public double getOtherPaid() {
        return otherPaid;
    }

    public void setOtherPaid(double otherPaid) {
        this.otherPaid = otherPaid;
    }

    public double getAmountAfterOtherPaid() {
        return amountAfterOtherPaid;
    }

    public void setAmountAfterOtherPaid(double amountAfterOtherPaid) {
        this.amountAfterOtherPaid = amountAfterOtherPaid;
    }
}
