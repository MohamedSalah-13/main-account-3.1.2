package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Sales_Package;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SalesPackageService(DaoFactory daoFactory) {

    public List<Sales_Package> fetchByInvoiceNumber(int id) throws DaoException {
        return daoFactory.salesPackageDao().loadAllById(id);
    }

}
