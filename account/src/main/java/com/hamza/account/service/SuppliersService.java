package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.party.PartyTableSpec.PartySearchScope;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.SuppliersDao;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

import java.util.List;

public record SuppliersService(DaoFactory daoFactory) {

    public List<Suppliers> getSuppliersList() throws DaoException {
        return nameDao().loadAll();
    }

    public SuppliersDao nameDao() {
        return daoFactory.getSuppliersDao();
    }

    public int save(Suppliers supplier) throws DaoException {
        AuthorizationGuard.require(supplier.getId() == 0
                ? AppPermissions.SUPPLIERS_CREATE : AppPermissions.SUPPLIERS_UPDATE);
        return TransactionTemplate.execute(() -> {
            int rows = supplier.getId() == 0 ? nameDao().insert(supplier) : nameDao().update(supplier);
            if (rows > 0) {
                ChangeAnnouncer announcer = ChangeAnnouncer.jdbc();
                announcer.announce(new NameChanged(PartyKind.SUPPLIER));
                announcer.announce(new AccountChanged(PartyKind.SUPPLIER));
            }
            return rows;
        });
    }

    public List<String> getNames() throws DaoException {
        return getSuppliersList().stream().map(Suppliers::getName).toList();
    }

    public Suppliers getNameById(int id) throws DaoException {
        return nameDao().getDataById(id);
    }

    /**
     * The name box's search. {@code scope} is the caller's decision and has no default
     * here on purpose - see {@link PartySearchScope}: an invoice must not offer a party
     * you have stopped dealing with, and a collection must still find one who owes you
     * money.
     */
    public List<Suppliers> getFilterSuppliers(String newValue, PartySearchScope scope) throws DaoException {
        return nameDao().getFilterSuppliers(newValue, scope);
    }

    public List<Suppliers> getSuppliers(int rowsPerPage, int offset) throws DaoException {
        return nameDao().getProducts(rowsPerPage, offset);
    }

    public int getCountItems() {
        return nameDao().getCountItems();
    }
}
