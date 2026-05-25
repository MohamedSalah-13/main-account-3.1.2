package com.hamza.account.model.domain;

import javafx.beans.property.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class InventoryItemModel {

    private final IntegerProperty itemId = new SimpleIntegerProperty();
    private final StringProperty barcode = new SimpleStringProperty();
    private final StringProperty nameItem = new SimpleStringProperty();

    private final IntegerProperty stockId = new SimpleIntegerProperty();
    private final StringProperty stockName = new SimpleStringProperty();

    private final StringProperty unitName = new SimpleStringProperty();

    private final DoubleProperty firstBalance = new SimpleDoubleProperty();
    private final DoubleProperty quantityPurchase = new SimpleDoubleProperty();
    private final DoubleProperty quantitySales = new SimpleDoubleProperty();
    private final DoubleProperty quantityPurchaseRe = new SimpleDoubleProperty();
    private final DoubleProperty quantitySalesRe = new SimpleDoubleProperty();
    private final DoubleProperty transferIn = new SimpleDoubleProperty();
    private final DoubleProperty transferOut = new SimpleDoubleProperty();

    private final DoubleProperty currentBalance = new SimpleDoubleProperty();

    private final DoubleProperty buyPrice = new SimpleDoubleProperty();
    private final DoubleProperty sellPrice = new SimpleDoubleProperty();

    private final DoubleProperty stockValueCost = new SimpleDoubleProperty();
    private final DoubleProperty stockValueSell = new SimpleDoubleProperty();

    private final StringProperty stockStatus = new SimpleStringProperty();

    public int getItemId() {
        return itemId.get();
    }

    public void setItemId(int itemId) {
        this.itemId.set(itemId);
    }

    public IntegerProperty itemIdProperty() {
        return itemId;
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

    public int getStockId() {
        return stockId.get();
    }

    public void setStockId(int stockId) {
        this.stockId.set(stockId);
    }

    public IntegerProperty stockIdProperty() {
        return stockId;
    }

    public String getStockName() {
        return stockName.get();
    }

    public void setStockName(String stockName) {
        this.stockName.set(stockName);
    }

    public StringProperty stockNameProperty() {
        return stockName;
    }

    public String getUnitName() {
        return unitName.get();
    }

    public void setUnitName(String unitName) {
        this.unitName.set(unitName);
    }

    public StringProperty unitNameProperty() {
        return unitName;
    }

    public double getFirstBalance() {
        return firstBalance.get();
    }

    public void setFirstBalance(double firstBalance) {
        this.firstBalance.set(firstBalance);
    }

    public DoubleProperty firstBalanceProperty() {
        return firstBalance;
    }

    public double getQuantityPurchase() {
        return quantityPurchase.get();
    }

    public void setQuantityPurchase(double quantityPurchase) {
        this.quantityPurchase.set(quantityPurchase);
    }

    public DoubleProperty quantityPurchaseProperty() {
        return quantityPurchase;
    }

    public double getQuantitySales() {
        return quantitySales.get();
    }

    public void setQuantitySales(double quantitySales) {
        this.quantitySales.set(quantitySales);
    }

    public DoubleProperty quantitySalesProperty() {
        return quantitySales;
    }

    public double getQuantityPurchaseRe() {
        return quantityPurchaseRe.get();
    }

    public void setQuantityPurchaseRe(double quantityPurchaseRe) {
        this.quantityPurchaseRe.set(quantityPurchaseRe);
    }

    public DoubleProperty quantityPurchaseReProperty() {
        return quantityPurchaseRe;
    }

    public double getQuantitySalesRe() {
        return quantitySalesRe.get();
    }

    public void setQuantitySalesRe(double quantitySalesRe) {
        this.quantitySalesRe.set(quantitySalesRe);
    }

    public DoubleProperty quantitySalesReProperty() {
        return quantitySalesRe;
    }

    public double getTransferIn() {
        return transferIn.get();
    }

    public void setTransferIn(double transferIn) {
        this.transferIn.set(transferIn);
    }

    public DoubleProperty transferInProperty() {
        return transferIn;
    }

    public double getTransferOut() {
        return transferOut.get();
    }

    public void setTransferOut(double transferOut) {
        this.transferOut.set(transferOut);
    }

    public DoubleProperty transferOutProperty() {
        return transferOut;
    }

    public double getCurrentBalance() {
        return currentBalance.get();
    }

    public void setCurrentBalance(double currentBalance) {
        this.currentBalance.set(currentBalance);
    }

    public DoubleProperty currentBalanceProperty() {
        return currentBalance;
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

    public double getSellPrice() {
        return sellPrice.get();
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice.set(sellPrice);
    }

    public DoubleProperty sellPriceProperty() {
        return sellPrice;
    }

    public double getStockValueCost() {
        return stockValueCost.get();
    }

    public void setStockValueCost(double stockValueCost) {
        this.stockValueCost.set(stockValueCost);
    }

    public DoubleProperty stockValueCostProperty() {
        return stockValueCost;
    }

    public double getStockValueSell() {
        return stockValueSell.get();
    }

    public void setStockValueSell(double stockValueSell) {
        this.stockValueSell.set(stockValueSell);
    }

    public DoubleProperty stockValueSellProperty() {
        return stockValueSell;
    }

    public String getStockStatus() {
        return stockStatus.get();
    }

    public void setStockStatus(String stockStatus) {
        this.stockStatus.set(stockStatus);
    }

    public StringProperty stockStatusProperty() {
        return stockStatus;
    }
}
