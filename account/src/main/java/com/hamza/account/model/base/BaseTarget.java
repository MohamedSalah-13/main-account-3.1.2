package com.hamza.account.model.base;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseTarget extends DForColumnTable {

    private DoubleProperty target_ratio1 = new SimpleDoubleProperty();
    private DoubleProperty target_ratio2 = new SimpleDoubleProperty();
    private DoubleProperty target_ratio3 = new SimpleDoubleProperty();
    private DoubleProperty rate1 = new SimpleDoubleProperty();
    private DoubleProperty rate2 = new SimpleDoubleProperty();
    private DoubleProperty rate3 = new SimpleDoubleProperty();

    public double getTarget_ratio1() {
        return target_ratio1.get();
    }

    public void setTarget_ratio1(double target_ratio1) {
        this.target_ratio1.set(target_ratio1);
    }

    public DoubleProperty target_ratio1Property() {
        return target_ratio1;
    }

    public double getTarget_ratio2() {
        return target_ratio2.get();
    }

    public void setTarget_ratio2(double target_ratio2) {
        this.target_ratio2.set(target_ratio2);
    }

    public DoubleProperty target_ratio2Property() {
        return target_ratio2;
    }

    public double getTarget_ratio3() {
        return target_ratio3.get();
    }

    public void setTarget_ratio3(double target_ratio3) {
        this.target_ratio3.set(target_ratio3);
    }

    public DoubleProperty target_ratio3Property() {
        return target_ratio3;
    }

    public double getRate1() {
        return rate1.get();
    }

    public void setRate1(double rate1) {
        this.rate1.set(rate1);
    }

    public DoubleProperty rate1Property() {
        return rate1;
    }

    public double getRate2() {
        return rate2.get();
    }

    public void setRate2(double rate2) {
        this.rate2.set(rate2);
    }

    public DoubleProperty rate2Property() {
        return rate2;
    }

    public double getRate3() {
        return rate3.get();
    }

    public void setRate3(double rate3) {
        this.rate3.set(rate3);
    }

    public DoubleProperty rate3Property() {
        return rate3;
    }
}
