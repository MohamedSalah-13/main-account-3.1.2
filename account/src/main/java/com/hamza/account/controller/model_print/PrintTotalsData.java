package com.hamza.account.controller.model_print;

import javafx.beans.property.*;
import lombok.Data;

@Data
public class PrintTotalsData {
    private IntegerProperty code;
    private StringProperty name;
    private StringProperty date;
    private StringProperty type_name;
    private DoubleProperty total;
    private DoubleProperty discount;
    private DoubleProperty total_amount;
    private DoubleProperty paid;

    public PrintTotalsData(int code, String name, String date, String type_name, double total, double discount, double total_amount
            , double paid) {
        this.code = new SimpleIntegerProperty(code);
        this.name = new SimpleStringProperty(name);
        this.date = new SimpleStringProperty(date);
        this.type_name = new SimpleStringProperty(type_name);
        this.total = new SimpleDoubleProperty(total);
        this.discount = new SimpleDoubleProperty(discount);
        this.total_amount = new SimpleDoubleProperty(total_amount);
        this.paid = new SimpleDoubleProperty(paid);
    }

    public int getCode() {
        return code.get();
    }

    public void setCode(int code) {
        this.code.set(code);
    }

    public IntegerProperty codeProperty() {
        return code;
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

    public String getDate() {
        return date.get();
    }

    public void setDate(String date) {
        this.date.set(date);
    }

    public StringProperty dateProperty() {
        return date;
    }

    public String getType_name() {
        return type_name.get();
    }

    public void setType_name(String type_name) {
        this.type_name.set(type_name);
    }

    public StringProperty type_nameProperty() {
        return type_name;
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

    public double getTotal_amount() {
        return total_amount.get();
    }

    public void setTotal_amount(double total_amount) {
        this.total_amount.set(total_amount);
    }

    public DoubleProperty total_amountProperty() {
        return total_amount;
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
}
