package com.hamza.account.model.base;


import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.TreasuryModel;
import com.hamza.account.type.TableName;
import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public abstract class BaseAccount extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private IntegerProperty id = new SimpleIntegerProperty();
    @ColumnData(titleName = NamesTables.DEBTOR)
    private DoubleProperty purchase = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.CREDITOR)
    private DoubleProperty paid = new SimpleDoubleProperty();
    @ColumnData(titleName = NamesTables.AMOUNT)
    private DoubleProperty amount = new SimpleDoubleProperty();
    private StringProperty type = new SimpleStringProperty();
    private StringProperty notes = new SimpleStringProperty();
    @ColumnData(titleName = NamesTables.DATE)
    private StringProperty date = new SimpleStringProperty();

    private IntegerProperty invoice_number = new SimpleIntegerProperty(0);
    private ObjectProperty<TreasuryModel> treasury = new SimpleObjectProperty<>();

    private TableName information;
    private String information_name;

    public BaseAccount(int id, String date, double paid, String notes, int invoice_number, TreasuryModel treasury) {
        this.id.set(id);
        this.date.set(date);
        this.paid.set(paid);
        this.notes.set(notes);
        this.invoice_number.set(invoice_number);
        this.setTreasury(treasury);
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
        return amount.get();
    }

    public void setAmount(double amount) {
        this.amount.set(amount);
    }

    public DoubleProperty amountProperty() {
        return amount;
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
        return notes.get();
    }

    public void setNotes(String notes) {
        this.notes.set(notes);
    }

    public StringProperty notesProperty() {
        return notes;
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
        return invoice_number.get();
    }

    public void setInvoice_number(int invoice_number) {
        this.invoice_number.set(invoice_number);
    }

    public IntegerProperty invoice_numberProperty() {
        return invoice_number;
    }

    public TreasuryModel getTreasury() {
        return treasury.get();
    }

    public void setTreasury(TreasuryModel treasury) {
        this.treasury.set(treasury);
    }

    public ObjectProperty<TreasuryModel> treasuryProperty() {
        return treasury;
    }

}
