package com.hamza.account.interfaces.spinner;

import com.hamza.account.model.domain.SupplierAccount;
import com.hamza.account.model.domain.Suppliers;

import java.util.function.Function;

public class SupplierSpinner implements SpinnerInterface<Suppliers, SupplierAccount> {


    @Override
    public Function<SupplierAccount, Double> getAmount() {
        return SupplierAccount::getAmount;
    }

    @Override
    public SupplierAccount objectAccount(Suppliers suppliers, double amount) {
        SupplierAccount customerAccount = new SupplierAccount();
        customerAccount.setAmount(amount);
        customerAccount.setSuppliers(new Suppliers(suppliers.getId(), suppliers.getName()));
        return customerAccount;
    }

    @Override
    public Suppliers objectName(SupplierAccount supplierAccount) {
        return supplierAccount.getSuppliers();
    }

    @Override
    public int nameId(Suppliers suppliers) {
        return suppliers.getId();
    }

    @Override
    public String nameString(Suppliers suppliers) {
        return suppliers.getName();
    }
}
