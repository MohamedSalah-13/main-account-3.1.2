package com.hamza.account.controller.reports.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class DataTable {
    private int code;
    private String nameData;
    private DoubleProperty purchase = new SimpleDoubleProperty();
    private DoubleProperty sales = new SimpleDoubleProperty();
    private DoubleProperty paidPurchase = new SimpleDoubleProperty();
    private DoubleProperty paidSales = new SimpleDoubleProperty();


    public DataTable(int code, String nameData, double purchase, double sales, double paidPurchase, double paidSales) {
        this.code = code;
        this.nameData = nameData;
        this.purchase = new SimpleDoubleProperty(purchase);
        this.sales = new SimpleDoubleProperty(sales);
        this.paidPurchase = new SimpleDoubleProperty(paidPurchase);
        this.paidSales = new SimpleDoubleProperty(paidSales);
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

    public double getSales() {
        return sales.get();
    }

    public void setSales(double sales) {
        this.sales.set(sales);
    }

    public DoubleProperty salesProperty() {
        return sales;
    }

    public double getPaidPurchase() {
        return paidPurchase.get();
    }

    public void setPaidPurchase(double paidPurchase) {
        this.paidPurchase.set(paidPurchase);
    }

    public DoubleProperty paidPurchaseProperty() {
        return paidPurchase;
    }

    public double getPaidSales() {
        return paidSales.get();
    }

    public void setPaidSales(double paidSales) {
        this.paidSales.set(paidSales);
    }

    public DoubleProperty paidSalesProperty() {
        return paidSales;
    }
}