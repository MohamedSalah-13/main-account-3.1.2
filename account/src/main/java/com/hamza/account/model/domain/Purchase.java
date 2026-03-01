package com.hamza.account.model.domain;

import com.hamza.account.model.base.BasePurchasesAndSales;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
public class Purchase extends BasePurchasesAndSales implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private ObjectProperty<Suppliers> suppliers = new SimpleObjectProperty<>();

    public Suppliers getSuppliers() {
        return suppliers.get();
    }

    public void setSuppliers(Suppliers suppliers) {
        this.suppliers.set(suppliers);
    }

    public ObjectProperty<Suppliers> suppliersProperty() {
        return suppliers;
    }
}


