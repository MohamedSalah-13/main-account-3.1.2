package com.hamza.account.controller.search;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.service.CustomerService;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;

import java.util.List;


public record CustomerSearchController(CustomerService customerService) implements SearchInterface<Customers> {

    @Override
    public List<TableColumn<Customers, ?>> columns() {
        return List.of(
                Columns.number(NamesTables.CODE, BaseNames::getId),
                Columns.text(NamesTables.NAME, BaseNames::getName),
                Columns.text(NamesTables.TEL, BaseNames::getTel),
                Columns.text(NamesTables.ADDRESS, BaseNames::getAddress),
                Columns.text(NamesTables.NOTES, BaseNames::getNotes),
                Columns.number(NamesTables.FIRST_BALANCE, BaseNames::getFirst_balance)
        );
    }

    @Override
    public String getName(Customers customers) {
        return customers.getName();
    }

    @Override
    public List<Customers> getFilterItems(String filter) throws Exception {
        return customerService.getFilterCustomers(filter);
    }
}
