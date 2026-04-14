package com.hamza.account.controller.model;

import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TableTotals {

    @ColumnData(titleName = "الاسم")
    private StringProperty name = new SimpleStringProperty();
    @ColumnData(titleName = "يناير")
    private DoubleProperty jan = new SimpleDoubleProperty();
    @ColumnData(titleName = "فبراير")
    private DoubleProperty feb = new SimpleDoubleProperty();
    @ColumnData(titleName = "مارس")
    private DoubleProperty mar = new SimpleDoubleProperty();
    @ColumnData(titleName = "إبريل")
    private DoubleProperty april = new SimpleDoubleProperty();
    @ColumnData(titleName = "مايو")
    private DoubleProperty may = new SimpleDoubleProperty();
    @ColumnData(titleName = "يونيو")
    private DoubleProperty jun = new SimpleDoubleProperty();
    @ColumnData(titleName = "يوليو")
    private DoubleProperty july = new SimpleDoubleProperty();
    @ColumnData(titleName = "أغسطس")
    private DoubleProperty aug = new SimpleDoubleProperty();
    @ColumnData(titleName = "سبتمبر")
    private DoubleProperty sep = new SimpleDoubleProperty();
    @ColumnData(titleName = "اكتوبر")
    private DoubleProperty oct = new SimpleDoubleProperty();
    @ColumnData(titleName = "نوفمبر")
    private DoubleProperty nov = new SimpleDoubleProperty();
    @ColumnData(titleName = "ديسمبر")
    private DoubleProperty des = new SimpleDoubleProperty();
    @ColumnData(titleName = "الاجمالى")
    private DoubleProperty totals = new SimpleDoubleProperty();

    public TableTotals(String name, double jan, double feb, double mar, double april, double may,
                       double jun, double july, double aug, double sep, double oct, double nov, double des, double totals) {
        this.name = new SimpleStringProperty(name);
        this.jan = new SimpleDoubleProperty(jan);
        this.feb = new SimpleDoubleProperty(feb);
        this.mar = new SimpleDoubleProperty(mar);
        this.april = new SimpleDoubleProperty(april);
        this.may = new SimpleDoubleProperty(may);
        this.jun = new SimpleDoubleProperty(jun);
        this.july = new SimpleDoubleProperty(july);
        this.aug = new SimpleDoubleProperty(aug);
        this.sep = new SimpleDoubleProperty(sep);
        this.oct = new SimpleDoubleProperty(oct);
        this.nov = new SimpleDoubleProperty(nov);
        this.des = new SimpleDoubleProperty(des);
        this.totals = new SimpleDoubleProperty(totals);
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

    public double getJan() {
        return jan.get();
    }

    public void setJan(double jan) {
        this.jan.set(jan);
    }

    public DoubleProperty janProperty() {
        return jan;
    }

    public double getFeb() {
        return feb.get();
    }

    public void setFeb(double feb) {
        this.feb.set(feb);
    }

    public DoubleProperty febProperty() {
        return feb;
    }

    public double getMar() {
        return mar.get();
    }

    public void setMar(double mar) {
        this.mar.set(mar);
    }

    public DoubleProperty marProperty() {
        return mar;
    }

    public double getApril() {
        return april.get();
    }

    public void setApril(double april) {
        this.april.set(april);
    }

    public DoubleProperty aprilProperty() {
        return april;
    }

    public double getMay() {
        return may.get();
    }

    public void setMay(double may) {
        this.may.set(may);
    }

    public DoubleProperty mayProperty() {
        return may;
    }

    public double getJun() {
        return jun.get();
    }

    public void setJun(double jun) {
        this.jun.set(jun);
    }

    public DoubleProperty junProperty() {
        return jun;
    }

    public double getJuly() {
        return july.get();
    }

    public void setJuly(double july) {
        this.july.set(july);
    }

    public DoubleProperty julyProperty() {
        return july;
    }

    public double getAug() {
        return aug.get();
    }

    public void setAug(double aug) {
        this.aug.set(aug);
    }

    public DoubleProperty augProperty() {
        return aug;
    }

    public double getSep() {
        return sep.get();
    }

    public void setSep(double sep) {
        this.sep.set(sep);
    }

    public DoubleProperty sepProperty() {
        return sep;
    }

    public double getOct() {
        return oct.get();
    }

    public void setOct(double oct) {
        this.oct.set(oct);
    }

    public DoubleProperty octProperty() {
        return oct;
    }

    public double getNov() {
        return nov.get();
    }

    public void setNov(double nov) {
        this.nov.set(nov);
    }

    public DoubleProperty novProperty() {
        return nov;
    }

    public double getDes() {
        return des.get();
    }

    public void setDes(double des) {
        this.des.set(des);
    }

    public DoubleProperty desProperty() {
        return des;
    }

    public double getTotals() {
        return totals.get();
    }

    public void setTotals(double totals) {
        this.totals.set(totals);
    }

    public DoubleProperty totalsProperty() {
        return totals;
    }
}
