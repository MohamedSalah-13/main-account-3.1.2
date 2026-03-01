package com.hamza.account.interfaces.spinner;

import com.hamza.account.model.domain.CustomerAccount;
import com.hamza.account.model.domain.Customers;

import java.util.function.Function;

public class CustomSpinner implements SpinnerInterface<Customers, CustomerAccount> {
    @Override
    public Function<CustomerAccount, Double> getAmount() {
        return CustomerAccount::getAmount;
    }

    @Override
    public CustomerAccount objectAccount(Customers customers, double amount) {
        CustomerAccount customerAccount = new CustomerAccount();
        customerAccount.setAmount(amount);
        customerAccount.setCustomers(new Customers(customers.getId(), customers.getName()));
        return customerAccount;
    }

    @Override
    public Customers objectName(CustomerAccount customerAccount) {
        return customerAccount.getCustomers();
    }

    @Override
    public int nameId(Customers customers) {
        return customers.getId();
    }

    @Override
    public String nameString(Customers customers) {
        return customers.getName();
    }

}
