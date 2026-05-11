package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.Stock;
import com.hamza.account.model.domain.Treasury;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceStatus;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseTotals extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.DATE)
    private StringProperty date = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.TOTAL)
    private DoubleProperty total = new SimpleDoubleProperty(0.0);
    @ColumnData(titleName = NamesTables.DISCOUNT)
    private DoubleProperty discount = new SimpleDoubleProperty(0.0);
    @ColumnData(titleName = NamesTables.TOTAL_AMOUNT)
    private DoubleProperty total_after_discount = new SimpleDoubleProperty(0.0);
    @ColumnData(titleName = NamesTables.CREDITOR)
    private DoubleProperty paid = new SimpleDoubleProperty(0.0);
    @ColumnData(titleName = NamesTables.REST)
    private DoubleProperty rest = new SimpleDoubleProperty(0.0);
    @ColumnData(titleName = NamesTables.NOTES)
    private StringProperty notes = new SimpleStringProperty();

    private ObjectProperty<Stock> stockData = new SimpleObjectProperty<>();
    private ObjectProperty<Treasury> treasuryModel = new SimpleObjectProperty<>();

    private DoubleProperty otherPaid = new SimpleDoubleProperty();
    private DoubleProperty amountAfterOtherPaid = new SimpleDoubleProperty();
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
        return rest.get();
    }

    public void setRest(double rest) {
        this.rest.set(rest);
    }

    public DoubleProperty restProperty() {
        return rest;
    }

    public String getNotes() {
        return notes.get();
    }

    public void setNotes(String notes) {
        this.notes.set(notes);
    }

    public StringProperty notesProperty() {
        return notes;
    }

    public Stock getStockData() {
        return stockData.get();
    }

    public void setStockData(Stock stockData) {
        this.stockData.set(stockData);
    }

    public ObjectProperty<Stock> stockDataProperty() {
        return stockData;
    }

    public Treasury getTreasuryModel() {
        return treasuryModel.get();
    }

    public void setTreasuryModel(Treasury treasury) {
        this.treasuryModel.set(treasury);
    }

    public ObjectProperty<Treasury> treasuryModelProperty() {
        return treasuryModel;
    }

    public double getOtherPaid() {
        return otherPaid.get();
    }

    public void setOtherPaid(double otherPaid) {
        this.otherPaid.set(otherPaid);
    }

    public DoubleProperty otherPaidProperty() {
        return otherPaid;
    }

    public double getAmountAfterOtherPaid() {
        return amountAfterOtherPaid.get();
    }

    public void setAmountAfterOtherPaid(double amountAfterOtherPaid) {
        this.amountAfterOtherPaid.set(amountAfterOtherPaid);
    }

    public DoubleProperty amountAfterOtherPaidProperty() {
        return amountAfterOtherPaid;
    }
}
