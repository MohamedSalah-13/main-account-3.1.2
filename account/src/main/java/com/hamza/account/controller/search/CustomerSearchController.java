package com.hamza.account.controller.search;

import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.service.CustomerService;

import java.util.List;


public record CustomerSearchController(CustomerService customerService) implements SearchInterface<Customers> {

    @Override
    public Class<? super Customers> getSearchClass() {
        return BaseNames.class;
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
