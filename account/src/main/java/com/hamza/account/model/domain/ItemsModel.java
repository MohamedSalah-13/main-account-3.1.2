package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.UnitExtends;
import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class ItemsModel extends UnitExtends {

    private IntegerProperty id = new SimpleIntegerProperty();
    private StringProperty barcode = new SimpleStringProperty();
    private StringProperty nameItem = new SimpleStringProperty();
    private DoubleProperty buyPrice = new SimpleDoubleProperty();
    private DoubleProperty selPrice1 = new SimpleDoubleProperty();
    private DoubleProperty selPrice2 = new SimpleDoubleProperty();
    private DoubleProperty selPrice3 = new SimpleDoubleProperty();
    private double mini_quantity;
    private double firstBalanceForStock;

    private SubGroups subGroups;
    private Stock itemStock;
    private byte[] item_image;

    private boolean activeItem;
    private boolean hasValidate;

    private int numberValidityDays;
    private int alertDaysBeforeExpiry;

    private List<ItemsUnitsModel> itemsUnitsModelList=new ArrayList<>();;
    private List<String> extraBarcodes = new ArrayList<>();

    private double sumAllBalance;
    private double sumPurchase;
    private double sumSales;
    private double sumPurchaseRe;
    private double sumSalesRe;
    private double fromStock;
    private double toStock;
    private double sumAllBalanceByBuyPrice;
    private double sumAllBalanceBySelPrice;

    public ItemsModel(Integer id) {
        initialize(id, null, null);
    }

    public ItemsModel(Integer id, String name) {
        initialize(id, null, name);
    }

    public ItemsModel(String barcode, String name) {
        initialize(null, barcode, name);
    }

    public ItemsModel(Integer id, String barcode, String name) {
        initialize(id, barcode, name);
    }

    private void initialize(Integer id, String barcode, String name) {
        if (id != null) {
            this.id = new SimpleIntegerProperty(id);
        }
        if (barcode != null) {
            this.barcode = new SimpleStringProperty(barcode);
        }
        if (name != null) {
            this.nameItem = new SimpleStringProperty(name);
        }
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

    public String getBarcode() {
        return barcode.get();
    }

    public void setBarcode(String barcode) {
        this.barcode.set(barcode);
    }

    public StringProperty barcodeProperty() {
        return barcode;
    }

    public String getNameItem() {
        return nameItem.get();
    }

    public void setNameItem(String nameItem) {
        this.nameItem.set(nameItem);
    }

    public StringProperty nameItemProperty() {
        return nameItem;
    }

    public double getBuyPrice() {
        return buyPrice.get();
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice.set(buyPrice);
    }

    public DoubleProperty buyPriceProperty() {
        return buyPrice;
    }

    public double getSelPrice1() {
        return selPrice1.get();
    }

    public void setSelPrice1(double selPrice1) {
        this.selPrice1.set(selPrice1);
    }

    public DoubleProperty selPrice1Property() {
        return selPrice1;
    }

    public double getSelPrice2() {
        return selPrice2.get();
    }

    public void setSelPrice2(double selPrice2) {
        this.selPrice2.set(selPrice2);
    }

    public DoubleProperty selPrice2Property() {
        return selPrice2;
    }

    public double getSelPrice3() {
        return selPrice3.get();
    }

    public void setSelPrice3(double selPrice3) {
        this.selPrice3.set(selPrice3);
    }

    public DoubleProperty selPrice3Property() {
        return selPrice3;
    }

    public double getMini_quantity() {
        return mini_quantity;
    }

    public void setMini_quantity(double mini_quantity) {
        this.mini_quantity = mini_quantity;
    }

    public SubGroups getSubGroups() {
        return subGroups;
    }

    public void setSubGroups(SubGroups subGroups) {
        this.subGroups = subGroups;
    }

    public Stock getItemStock() {
        return itemStock;
    }

    public void setItemStock(Stock itemStock) {
        this.itemStock = itemStock;
    }

    public double getFirstBalanceForStock() {
        return firstBalanceForStock;
    }

    public void setFirstBalanceForStock(double firstBalanceForStock) {
        this.firstBalanceForStock = firstBalanceForStock;
    }

    public double getSumAllBalance() {
        return sumAllBalance;
    }

    public void setSumAllBalance(double sumAllBalance) {
        this.sumAllBalance = sumAllBalance;
    }

    public double getSumPurchase() {
        return sumPurchase;
    }

    public void setSumPurchase(double sumPurchase) {
        this.sumPurchase = sumPurchase;
    }

    public double getSumSales() {
        return sumSales;
    }

    public void setSumSales(double sumSales) {
        this.sumSales = sumSales;
    }

    public double getSumPurchaseRe() {
        return sumPurchaseRe;
    }

    public void setSumPurchaseRe(double sumPurchaseRe) {
        this.sumPurchaseRe = sumPurchaseRe;
    }

    public double getSumSalesRe() {
        return sumSalesRe;
    }

    public void setSumSalesRe(double sumSalesRe) {
        this.sumSalesRe = sumSalesRe;
    }

    public double getFromStock() {
        return fromStock;
    }

    public void setFromStock(double fromStock) {
        this.fromStock = fromStock;
    }

    public double getToStock() {
        return toStock;
    }

    public void setToStock(double toStock) {
        this.toStock = toStock;
    }

    public double getSumAllBalanceByBuyPrice() {
        return sumAllBalanceByBuyPrice;
    }

    public void setSumAllBalanceByBuyPrice(double sumAllBalanceByBuyPrice) {
        this.sumAllBalanceByBuyPrice = sumAllBalanceByBuyPrice;
    }

    public double getSumAllBalanceBySelPrice() {
        return sumAllBalanceBySelPrice;
    }

    public void setSumAllBalanceBySelPrice(double sumAllBalanceBySelPrice) {
        this.sumAllBalanceBySelPrice = sumAllBalanceBySelPrice;
    }

    public boolean isActiveItem() {
        return activeItem;
    }

    public void setActiveItem(boolean activeItem) {
        this.activeItem = activeItem;
    }
}
