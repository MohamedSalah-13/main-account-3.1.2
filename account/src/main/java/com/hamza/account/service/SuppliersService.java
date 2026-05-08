package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.SuppliersDao;
import com.hamza.account.model.domain.Customers;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SuppliersService(DaoFactory daoFactory) {

    public List<Suppliers> getSuppliersList() throws DaoException {
        return nameDao().loadAll();
    }

    public SuppliersDao nameDao() {
        return daoFactory.getSuppliersDao();
    }

    public List<String> getNames() throws DaoException {
        return getSuppliersList().stream().map(Suppliers::getName).toList();
    }

    public Suppliers getNameById(int id) throws DaoException {
        return nameDao().getDataById(id);
    }

    public List<Suppliers> getFilterSuppliers(String newValue) throws DaoException {
        return nameDao().getFilterSuppliers(newValue);
    }
}
