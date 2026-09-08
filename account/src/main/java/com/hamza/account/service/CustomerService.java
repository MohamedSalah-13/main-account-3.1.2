package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.model.dao.CustomerDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.features.events.AccountChanged;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.NameChanged;
import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

import java.util.List;

public record CustomerService(DaoFactory daoFactory) {

    public List<Customers> getCustomerList() throws DaoException {
        return nameDao().loadAll();
    }

    public List<String> getNames() throws DaoException {
        return getCustomerList().stream().map(Customers::getName).toList();
    }

    public CustomerDao nameDao() {
        return daoFactory.customersDao();
    }

    public int save(Customers customer) throws DaoException {
        AuthorizationGuard.require(customer.getId() == 0
                ? AppPermissions.CUSTOMER_CREATE : AppPermissions.CUSTOMER_UPDATE);
        return TransactionTemplate.execute(() -> {
            int rows = customer.getId() == 0 ? nameDao().insert(customer) : nameDao().update(customer);
            if (rows > 0) {
                ChangeAnnouncer announcer = ChangeAnnouncer.jdbc();
                announcer.announce(new NameChanged(PartyKind.CUSTOMER));
                announcer.announce(new AccountChanged(PartyKind.CUSTOMER));
            }
            return rows;
        });
    }

    public Customers getCustomerById(int id) throws DaoException {
        return nameDao().getDataById(id);
    }

    public List<Customers> getFilterCustomers(String newValue) throws DaoException {
        return nameDao().getFilterCustomers(newValue);
    }

    public List<Customers> getCustomers(int rowsPerPage, int offset) throws DaoException {
        return nameDao().getProducts(rowsPerPage, offset);
    }

    public int getCountCustomers() {
        return nameDao().getCountItems();
    }

}
