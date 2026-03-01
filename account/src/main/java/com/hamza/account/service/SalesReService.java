package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Sales_Return;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SalesReService(DaoFactory daoFactory) {

    public List<Sales_Return> fetchByInvoiceNumber(int invoiceNumber) throws DaoException {
        return daoFactory.salesReturnsDao().loadAllById(invoiceNumber);
    }

    public List<Sales_Return> findBetweenTwoInvoiceNumber(int firstId, int lastId) throws DaoException {
        return daoFactory.salesReturnsDao().loadBetweenTwoInvoiceNumber(firstId, lastId);
    }

    public List<Sales_Return> findByNumItem(int numItem) throws DaoException {
        return daoFactory.salesReturnsDao().findByNumItem(numItem);
    }

}
