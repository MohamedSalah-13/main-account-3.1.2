package com.hamza.account.controller.reports;

import javafx.beans.property.*;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TreeArea {
    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty name = new SimpleStringProperty();
    private DoubleProperty purchase = new SimpleDoubleProperty();
    private DoubleProperty sale = new SimpleDoubleProperty();
    private DoubleProperty amount = new SimpleDoubleProperty();

    public TreeArea(int id, String name, double purchase, double sale, double amount) {
        this.id.set(id);
        this.name.set(name);
        this.purchase.set(purchase);
        this.sale.set(sale);
        this.amount.set(amount);
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

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
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

    public double getSale() {
        return sale.get();
    }

    public void setSale(double sale) {
        this.sale.set(sale);
    }

    public DoubleProperty saleProperty() {
        return sale;
    }

    public double getAmount() {
        return amount.get();
    }

    public void setAmount(double amount) {
        this.amount.set(amount);
    }

    public DoubleProperty amountProperty() {
        return amount;
    }
}
