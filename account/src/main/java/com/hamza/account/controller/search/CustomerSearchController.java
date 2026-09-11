package com.hamza.account.controller.search;

import com.hamza.account.config.NamesTables;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.service.CustomerService;
import com.hamza.controlsfx.table.Columns;
import javafx.scene.control.TableColumn;

import java.util.List;


/**
 * The party picker behind {@code PartySuggestionField}.
 *
 * @param scope whether a party who is no longer dealt with may be offered. It is a
 *              constructor argument rather than a constant because the same picker
 *              serves an invoice, which must not offer one, and a collection, which
 *              must - see {@link PartySearchScope}.
 */
public record CustomerSearchController(CustomerService customerService, PartySearchScope scope) implements SearchInterface<Customers> {

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
        return customerService.getFilterCustomers(filter, scope);
    }
}
