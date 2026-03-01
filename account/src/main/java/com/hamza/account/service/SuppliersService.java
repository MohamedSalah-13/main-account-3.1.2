package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.SuppliersDao;
import com.hamza.account.model.domain.Suppliers;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SuppliersService(DaoFactory daoFactory) {

    public List<Suppliers> getSuppliersList() throws DaoException {
//        return LoadDataAndList.getListSuppliers();
        return daoFactory.getSuppliersDao().loadAll();
    }

    public SuppliersDao nameDao() {
        return daoFactory.getSuppliersDao();
    }

    public List<String> getNames() throws DaoException {
        return getSuppliersList().stream().map(Suppliers::getName).toList();
    }

    public Suppliers getNameById(int id) throws DaoException {
        return daoFactory.getSuppliersDao().getDataById(id);
    }
}
