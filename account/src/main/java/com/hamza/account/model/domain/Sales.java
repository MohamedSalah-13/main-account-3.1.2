package com.hamza.account.model.domain;

import com.hamza.account.model.base.BasePurchasesAndSales;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class Sales extends BasePurchasesAndSales implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private boolean item_has_package;
    private ObjectProperty<Customers> customers = new SimpleObjectProperty<>();

    // for totals
    private LocalDate invoiceDate;
    private int stock_id;

    public Customers getCustomers() {
        return customers.get();
    }

    public void setCustomers(Customers customers) {
        this.customers.set(customers);
    }

    public ObjectProperty<Customers> customersProperty() {
        return customers;
    }

    @Override
    public String toString() {
        return "Sales{" +
                "id=" + getId() +
                ", item_has_package=" + item_has_package +
                ", invoiceDate=" + invoiceDate +
                ", stock_id=" + stock_id +
                '}';
    }
}


