package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseAccount;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class SupplierAccount extends BaseAccount {

    private ObjectProperty<Suppliers> suppliers = new SimpleObjectProperty<>();

    public SupplierAccount(int num, String date, double paid, String notes, Integer invoice_number, Suppliers suppliers, TreasuryModel treasury) {
        super(num, date, paid, notes, invoice_number, treasury);
        this.suppliers = new SimpleObjectProperty<>(suppliers);
    }

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
