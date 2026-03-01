package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Sales;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record SalesService(DaoFactory daoFactory) {

    public List<Sales> fetchByInvoiceNumber(int invoiceNumber) throws DaoException {
        return daoFactory.salesDao().loadAllById(invoiceNumber);
    }

    public List<Sales> findBetweenTwoInvoiceNumber(int firstId, int lastId) throws DaoException {
        return daoFactory.salesDao().loadBetweenTwoInvoiceNumber(firstId, lastId);
//        return getSalesList().stream().filter(s -> s.getInvoiceNumber() >= firstId && s.getInvoiceNumber() <= lastId).toList();
    }

    public List<Sales> findByNumItem(int numItem) throws DaoException {
        return daoFactory.salesDao().findByNumItem(numItem);
    }
}
