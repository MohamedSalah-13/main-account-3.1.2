package com.hamza.account.controller.pos;

import com.hamza.account.model.base.BasePurchasesAndSales;
import javafx.beans.property.*;
import javafx.collections.ObservableList;

public class PosInvoiceSettingData {

    protected BooleanProperty isChangeData = new SimpleBooleanProperty(false);
    protected IntegerProperty customerId = new SimpleIntegerProperty(0);
    protected StringProperty textCode = new SimpleStringProperty(null);
    protected StringProperty textCustomName = new SimpleStringProperty(null);
    protected StringProperty textTel = new SimpleStringProperty(null);
    protected StringProperty textAddress = new SimpleStringProperty(null);
    protected StringProperty comboArea = new SimpleStringProperty(null);
    protected StringProperty textTotal = new SimpleStringProperty(null);
    protected StringProperty textDiscount = new SimpleStringProperty(null);
    protected StringProperty textAddons = new SimpleStringProperty(null);
    protected ListProperty<BasePurchasesAndSales> invoiceItems = new SimpleListProperty<>();


    protected double parseDouble(String text) {
        try {
            return Double.parseDouble(text == null ? "0" : text.trim());
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    public boolean isIsChangeData() {
        return isChangeData.get();
    }

    public void setIsChangeData(boolean isChangeData) {
        this.isChangeData.set(isChangeData);
    }

    public BooleanProperty isChangeDataProperty() {
        return isChangeData;
    }

    public int getCustomerId() {
        return customerId.get();
    }

    public void setCustomerId(int customerId) {
        this.customerId.set(customerId);
    }

    public IntegerProperty customerIdProperty() {
        return customerId;
    }

    public String getTextCode() {
        return textCode.get();
    }

    public void setTextCode(String textCode) {
        this.textCode.set(textCode);
    }

    public StringProperty textCodeProperty() {
        return textCode;
    }

    public String getTextCustomName() {
        return textCustomName.get();
    }

    public void setTextCustomName(String textCustomName) {
        this.textCustomName.set(textCustomName);
    }

    public StringProperty textCustomNameProperty() {
        return textCustomName;
    }

    public String getTextTel() {
        return textTel.get();
    }

    public void setTextTel(String textTel) {
        this.textTel.set(textTel);
    }

    public StringProperty textTelProperty() {
        return textTel;
    }

    public String getTextAddress() {
        return textAddress.get();
    }

    public void setTextAddress(String textAddress) {
        this.textAddress.set(textAddress);
    }

    public StringProperty textAddressProperty() {
        return textAddress;
    }

    public String getComboArea() {
        return comboArea.get();
    }

    public void setComboArea(String comboArea) {
        this.comboArea.set(comboArea);
    }

    public StringProperty comboAreaProperty() {
        return comboArea;
    }

    public String getTextTotal() {
        return textTotal.get();
    }

    public void setTextTotal(String textTotal) {
        this.textTotal.set(textTotal);
    }

    public StringProperty textTotalProperty() {
        return textTotal;
    }

    public String getTextDiscount() {
        return textDiscount.get();
    }

    public void setTextDiscount(String textDiscount) {
        this.textDiscount.set(textDiscount);
    }

    public StringProperty textDiscountProperty() {
        return textDiscount;
    }

    public String getTextAddons() {
        return textAddons.get();
    }

    public void setTextAddons(String textAddons) {
        this.textAddons.set(textAddons);
    }

    public StringProperty textAddonsProperty() {
        return textAddons;
    }

    public ObservableList<BasePurchasesAndSales> getInvoiceItems() {
        return invoiceItems.get();
    }

    public void setInvoiceItems(ObservableList<BasePurchasesAndSales> invoiceItems) {
        this.invoiceItems.set(invoiceItems);
    }

    public ListProperty<BasePurchasesAndSales> invoiceItemsProperty() {
        return invoiceItems;
    }

}
