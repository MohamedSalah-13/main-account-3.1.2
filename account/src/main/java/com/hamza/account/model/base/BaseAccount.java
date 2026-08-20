package com.hamza.account.model.base;


import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.TableName;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public abstract class BaseAccount extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private DoubleProperty purchase = new SimpleDoubleProperty();
    private DoubleProperty paid = new SimpleDoubleProperty();
    private double amount;
    private StringProperty type = new SimpleStringProperty();
    private String notes;
    private StringProperty date = new SimpleStringProperty();

    private int invoice_number;
    private Treasury treasury;

    private TableName information;
    private String information_name;

    public BaseAccount(int id, String date, double paid, String notes, int invoice_number, Treasury treasury) {
        this.id.set(id);
        this.date.set(date);
        this.paid.set(paid);
        this.notes = notes;
        this.invoice_number = invoice_number;
        this.treasury = treasury;
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

    public double getPurchase() {
        return purchase.get();
    }

    public void setPurchase(double purchase) {
        this.purchase.set(purchase);
    }

    public DoubleProperty purchaseProperty() {
        return purchase;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getType() {
        return type.get();
    }

    public void setType(String type) {
        this.type.set(type);
    }

    public StringProperty typeProperty() {
        return type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public int getInvoice_number() {
        return invoice_number;
    }

    public void setInvoice_number(int invoice_number) {
        this.invoice_number = invoice_number;
    }

    public Treasury getTreasury() {
        return treasury;
    }

    public void setTreasury(Treasury treasury) {
        this.treasury = treasury;
    }

}
