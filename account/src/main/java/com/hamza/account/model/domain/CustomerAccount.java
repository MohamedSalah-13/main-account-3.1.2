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
public class CustomerAccount extends BaseAccount {

    private ObjectProperty<Customers> customers = new SimpleObjectProperty<>();
    // this use for print account
    private int area_id;
    private String area_name;

    public CustomerAccount(int num, String date, double paid, String notes, Integer invoice_number, Customers customers, Treasury treasury) {
        super(num, date, paid, notes, invoice_number, treasury);
        this.customers = new SimpleObjectProperty<>(customers);
    }

    public Customers getCustomers() {
        return customers.get();
    }

    public void setCustomers(Customers customers) {
        this.customers.set(customers);
    }

    public ObjectProperty<Customers> customersProperty() {
        return customers;
    }

}
