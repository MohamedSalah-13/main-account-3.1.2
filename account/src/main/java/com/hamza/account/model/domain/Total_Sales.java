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
@NoArgsConstructor
@AllArgsConstructor
public class Total_Sales extends BaseTotals {

    private ObjectProperty<Customers> customers = new SimpleObjectProperty<>();
    private ObjectProperty<Employees> employeeObject = new SimpleObjectProperty<>();
    private List<Sales> salesList = new ArrayList<>();
    private double total_profit;
    private double profit_percent;

    public Total_Sales(int i) {
        this.setId(i);
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

    public Employees getEmployeeObject() {
        return employeeObject.get();
    }

    public void setEmployeeObject(Employees employeeObject) {
        this.employeeObject.set(employeeObject);
    }

    public ObjectProperty<Employees> employeeObjectProperty() {
        return employeeObject;
    }
}
