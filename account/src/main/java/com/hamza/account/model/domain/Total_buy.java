package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseTotals;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Total_buy extends BaseTotals {

    private ObjectProperty<Suppliers> supplierData = new SimpleObjectProperty<>();
    private List<Purchase> purchaseList = new ArrayList<>();

    public Suppliers getSupplierData() {
        return supplierData.get();
    }

    public void setSupplierData(Suppliers supplierData) {
        this.supplierData.set(supplierData);
    }

    public ObjectProperty<Suppliers> supplierDataProperty() {
        return supplierData;
    }
}
