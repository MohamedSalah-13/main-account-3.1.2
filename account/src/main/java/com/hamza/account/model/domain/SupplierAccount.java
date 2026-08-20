package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseAccount;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class SupplierAccount extends BaseAccount {

    private Suppliers suppliers;

    public SupplierAccount(int num, String date, double paid, String notes, Integer invoice_number, Suppliers suppliers, Treasury treasury) {
        super(num, date, paid, notes, invoice_number, treasury);
        this.suppliers = suppliers;
    }

}
