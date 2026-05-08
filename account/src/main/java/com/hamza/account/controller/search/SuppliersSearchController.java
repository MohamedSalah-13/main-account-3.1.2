package com.hamza.account.controller.search;

import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.service.SuppliersService;

import java.util.List;


public record SuppliersSearchController(SuppliersService suppliersService) implements SearchInterface<Suppliers> {

    @Override
    public Class<? super Suppliers> getSearchClass() {
        return BaseNames.class;
    }

    @Override
    public String getName(Suppliers suppliers) {
        return suppliers.getName();
    }

    @Override
    public List<Suppliers> getFilterItems(String filter) throws Exception {
        return suppliersService.getFilterSuppliers(filter);
    }

}
