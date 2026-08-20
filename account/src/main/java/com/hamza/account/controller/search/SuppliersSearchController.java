package com.hamza.account.controller.search;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.service.SuppliersService;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;

import java.util.List;


public record SuppliersSearchController(SuppliersService suppliersService) implements SearchInterface<Suppliers> {

    @Override
    public List<TableColumn<Suppliers, ?>> columns() {
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
    public String getName(Suppliers suppliers) {
        return suppliers.getName();
    }

    @Override
    public List<Suppliers> getFilterItems(String filter) throws Exception {
        return suppliersService.getFilterSuppliers(filter);
    }

}
