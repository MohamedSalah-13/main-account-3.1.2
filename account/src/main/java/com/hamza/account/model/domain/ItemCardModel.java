package com.hamza.account.model.domain;

import com.hamza.controlsfx.table.ColumnData;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class ItemCardModel {

    @ColumnData(titleName = "رقم الصنف")
    private IntegerProperty itemId = new SimpleIntegerProperty();

    @ColumnData(titleName = "الباركود")
    private StringProperty barcode = new SimpleStringProperty();

    @ColumnData(titleName = "اسم الصنف")
    private StringProperty itemName = new SimpleStringProperty();

    @ColumnData(titleName = "المخزن")
    private StringProperty stockName = new SimpleStringProperty();

    @ColumnData(titleName = "الوحدة")
    private StringProperty unitName = new SimpleStringProperty();

    @ColumnData(titleName = "التاريخ")
    private ObjectProperty<LocalDate> movementDate = new SimpleObjectProperty<>();

    @ColumnData(titleName = "نوع الحركة")
    private StringProperty movementTypeAr = new SimpleStringProperty();

    @ColumnData(titleName = "وارد")
    private DoubleProperty quantityIn = new SimpleDoubleProperty();

    @ColumnData(titleName = "صادر")
    private DoubleProperty quantityOut = new SimpleDoubleProperty();

    @ColumnData(titleName = "الرصيد بعد الحركة")
    private DoubleProperty runningBalance = new SimpleDoubleProperty();

    @ColumnData(titleName = "رقم الفاتورة/المرجع")
    private LongProperty invoiceNumber = new LongPropertyBase() {
        @Override
        protected void invalidated() {}
        @Override
        public Object getBean() { return ItemCardModel.this; }
        @Override
        public String getName() { return "invoiceNumber"; }
    };

    @ColumnData(titleName = "الطرف الآخر")
    private StringProperty partyName = new SimpleStringProperty();

    @ColumnData(titleName = "سعر الوحدة")
    private DoubleProperty price = new SimpleDoubleProperty();

    @ColumnData(titleName = "ملاحظات")
    private StringProperty notes = new SimpleStringProperty();

    @ColumnData(titleName = "المستخدم")
    private StringProperty userName = new SimpleStringProperty();

    // getters and setters for properties
    public int getItemId() { return itemId.get(); }
    public void setItemId(int itemId) { this.itemId.set(itemId); }
    public IntegerProperty itemIdProperty() { return itemId; }

    public String getBarcode() { return barcode.get(); }
    public void setBarcode(String barcode) { this.barcode.set(barcode); }
    public StringProperty barcodeProperty() { return barcode; }

    public String getItemName() { return itemName.get(); }
    public void setItemName(String itemName) { this.itemName.set(itemName); }
    public StringProperty itemNameProperty() { return itemName; }

    public String getStockName() { return stockName.get(); }
    public void setStockName(String stockName) { this.stockName.set(stockName); }
    public StringProperty stockNameProperty() { return stockName; }

    public String getUnitName() { return unitName.get(); }
    public void setUnitName(String unitName) { this.unitName.set(unitName); }
    public StringProperty unitNameProperty() { return unitName; }

    public LocalDate getMovementDate() { return movementDate.get(); }
    public void setMovementDate(LocalDate movementDate) { this.movementDate.set(movementDate); }
    public ObjectProperty<LocalDate> movementDateProperty() { return movementDate; }

    public String getMovementTypeAr() { return movementTypeAr.get(); }
    public void setMovementTypeAr(String movementTypeAr) { this.movementTypeAr.set(movementTypeAr); }
    public StringProperty movementTypeArProperty() { return movementTypeAr; }

    public double getQuantityIn() { return quantityIn.get(); }
    public void setQuantityIn(double quantityIn) { this.quantityIn.set(quantityIn); }
    public DoubleProperty quantityInProperty() { return quantityIn; }

    public double getQuantityOut() { return quantityOut.get(); }
    public void setQuantityOut(double quantityOut) { this.quantityOut.set(quantityOut); }
    public DoubleProperty quantityOutProperty() { return quantityOut; }

    public double getRunningBalance() { return runningBalance.get(); }
    public void setRunningBalance(double runningBalance) { this.runningBalance.set(runningBalance); }
    public DoubleProperty runningBalanceProperty() { return runningBalance; }

    public long getInvoiceNumber() { return invoiceNumber.get(); }
    public void setInvoiceNumber(long invoiceNumber) { this.invoiceNumber.set(invoiceNumber); }
    public LongProperty invoiceNumberProperty() { return invoiceNumber; }

    public String getPartyName() { return partyName.get(); }
    public void setPartyName(String partyName) { this.partyName.set(partyName); }
    public StringProperty partyNameProperty() { return partyName; }

    public double getPrice() { return price.get(); }
    public void setPrice(double price) { this.price.set(price); }
    public DoubleProperty priceProperty() { return price; }

    public String getNotes() { return notes.get(); }
    public void setNotes(String notes) { this.notes.set(notes); }
    public StringProperty notesProperty() { return notes; }

    public String getUserName() { return userName.get(); }
    public void setUserName(String userName) { this.userName.set(userName); }
    public StringProperty userNameProperty() { return userName; }
}
