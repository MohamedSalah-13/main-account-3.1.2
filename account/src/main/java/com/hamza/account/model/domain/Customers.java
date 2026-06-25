package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseNames;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Customers extends BaseNames {

    private DoubleProperty credit_limit = new SimpleDoubleProperty();
    private ObjectProperty<SelPriceTypeModel> selPriceObject = new SimpleObjectProperty<>();

    public Customers(int id) {
        setId(id);
    }

    public Customers(int id, String name) {
        setId(id);
        setName(name);
    }

    public Customers(String name, double limit, double firstBalance) {
        setName(name);
        setFirst_balance(firstBalance);
        this.credit_limit = new SimpleDoubleProperty(limit);
    }

    public double getCredit_limit() {
        return credit_limit.get();
    }

    public void setCredit_limit(double credit_limit) {
        this.credit_limit.set(credit_limit);
    }

    public DoubleProperty credit_limitProperty() {
        return credit_limit;
    }

    public SelPriceTypeModel getSelPriceObject() {
        return selPriceObject.get();
    }

    public void setSelPriceObject(SelPriceTypeModel selPriceObject) {
        this.selPriceObject.set(selPriceObject);
    }

    public ObjectProperty<SelPriceTypeModel> selPriceObjectProperty() {
        return selPriceObject;
    }
}
