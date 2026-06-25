package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class DelegatePerformanceReport extends DForColumnTable {

    @ColumnData(titleName = "كود المندوب")
    private int delegateId;

    @ColumnData(titleName = "المندوب")
    private final StringProperty delegateName = new SimpleStringProperty("");

    @ColumnData(titleName = "التاريخ")
    private final ObjectProperty<LocalDate> reportDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "عدد الفواتير")
    private final IntegerProperty invoicesCount = new SimpleIntegerProperty(0);

    @ColumnData(titleName = "عدد العملاء")
    private final IntegerProperty customersCount = new SimpleIntegerProperty(0);

    @ColumnData(titleName = "إجمالي المبيعات")
    private final DoubleProperty grossSales = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "خصم المبيعات")
    private final DoubleProperty salesDiscount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "عدد المرتجعات")
    private final IntegerProperty returnsCount = new SimpleIntegerProperty(0);

    @ColumnData(titleName = "إجمالي المرتجعات")
    private final DoubleProperty grossReturns = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "خصم المرتجعات")
    private final DoubleProperty returnsDiscount = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "صافي المبيعات")
    private final DoubleProperty netSales = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "تحصيل الفواتير")
    private final DoubleProperty invoiceCashCollected = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "تحصيل الحسابات")
    private final DoubleProperty accountCollections = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "إجمالي التحصيل")
    private final DoubleProperty totalCollected = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "كاش المرتجعات")
    private final DoubleProperty returnedCash = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "صافي الربح")
    private final DoubleProperty netProfit = new SimpleDoubleProperty(0);

    @ColumnData(titleName = "نسبة الربح")
    private final DoubleProperty profitPercent = new SimpleDoubleProperty(0);

    public String getDelegateName() {
        return delegateName.get();
    }

    public void setDelegateName(String delegateName) {
        this.delegateName.set(delegateName);
    }

    public StringProperty delegateNameProperty() {
        return delegateName;
    }

    public LocalDate getReportDate() {
        return reportDate.get();
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate.set(reportDate);
    }

    public ObjectProperty<LocalDate> reportDateProperty() {
        return reportDate;
    }

    public int getInvoicesCount() {
        return invoicesCount.get();
    }

    public void setInvoicesCount(int invoicesCount) {
        this.invoicesCount.set(invoicesCount);
    }

    public IntegerProperty invoicesCountProperty() {
        return invoicesCount;
    }

    public int getCustomersCount() {
        return customersCount.get();
    }

    public void setCustomersCount(int customersCount) {
        this.customersCount.set(customersCount);
    }

    public IntegerProperty customersCountProperty() {
        return customersCount;
    }

    public double getGrossSales() {
        return grossSales.get();
    }

    public void setGrossSales(double grossSales) {
        this.grossSales.set(grossSales);
    }

    public DoubleProperty grossSalesProperty() {
        return grossSales;
    }

    public double getSalesDiscount() {
        return salesDiscount.get();
    }

    public void setSalesDiscount(double salesDiscount) {
        this.salesDiscount.set(salesDiscount);
    }

    public DoubleProperty salesDiscountProperty() {
        return salesDiscount;
    }

    public int getReturnsCount() {
        return returnsCount.get();
    }

    public void setReturnsCount(int returnsCount) {
        this.returnsCount.set(returnsCount);
    }

    public IntegerProperty returnsCountProperty() {
        return returnsCount;
    }

    public double getGrossReturns() {
        return grossReturns.get();
    }

    public void setGrossReturns(double grossReturns) {
        this.grossReturns.set(grossReturns);
    }

    public DoubleProperty grossReturnsProperty() {
        return grossReturns;
    }

    public double getReturnsDiscount() {
        return returnsDiscount.get();
    }

    public void setReturnsDiscount(double returnsDiscount) {
        this.returnsDiscount.set(returnsDiscount);
    }

    public DoubleProperty returnsDiscountProperty() {
        return returnsDiscount;
    }

    public double getNetSales() {
        return netSales.get();
    }

    public void setNetSales(double netSales) {
        this.netSales.set(netSales);
    }

    public DoubleProperty netSalesProperty() {
        return netSales;
    }

    public double getInvoiceCashCollected() {
        return invoiceCashCollected.get();
    }

    public void setInvoiceCashCollected(double invoiceCashCollected) {
        this.invoiceCashCollected.set(invoiceCashCollected);
    }

    public DoubleProperty invoiceCashCollectedProperty() {
        return invoiceCashCollected;
    }

    public double getAccountCollections() {
        return accountCollections.get();
    }

    public void setAccountCollections(double accountCollections) {
        this.accountCollections.set(accountCollections);
    }

    public DoubleProperty accountCollectionsProperty() {
        return accountCollections;
    }

    public double getTotalCollected() {
        return totalCollected.get();
    }

    public void setTotalCollected(double totalCollected) {
        this.totalCollected.set(totalCollected);
    }

    public DoubleProperty totalCollectedProperty() {
        return totalCollected;
    }

    public double getReturnedCash() {
        return returnedCash.get();
    }

    public void setReturnedCash(double returnedCash) {
        this.returnedCash.set(returnedCash);
    }

    public DoubleProperty returnedCashProperty() {
        return returnedCash;
    }

    public double getNetProfit() {
        return netProfit.get();
    }

    public void setNetProfit(double netProfit) {
        this.netProfit.set(netProfit);
    }

    public DoubleProperty netProfitProperty() {
        return netProfit;
    }

    public double getProfitPercent() {
        return profitPercent.get();
    }

    public void setProfitPercent(double profitPercent) {
        this.profitPercent.set(profitPercent);
    }

    public DoubleProperty profitPercentProperty() {
        return profitPercent;
    }
}
