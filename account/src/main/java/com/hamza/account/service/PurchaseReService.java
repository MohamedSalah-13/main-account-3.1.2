package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase_Return;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record PurchaseReService(DaoFactory daoFactory) {

    public List<Purchase_Return> fetchByInvoiceNumber(int invoiceNumber) throws DaoException {
//        return getAllPurchaseReturns().stream().filter(p -> p.getInvoiceNumber() == invoiceNumber).toList();
        return daoFactory.purchaseReturnsDao().loadAllById(invoiceNumber);
    }

    public List<Purchase_Return> findBetweenTwoInvoiceNumber(int startInvoiceNumber, int endInvoiceNumber) throws DaoException {
        return daoFactory.purchaseReturnsDao().loadBetweenTwoInvoiceNumber(startInvoiceNumber, endInvoiceNumber);
    }

    public List<Purchase_Return> findByNumItem(int numItem) throws DaoException {
        return daoFactory.purchaseReturnsDao().findByNumItem(numItem);
    }

}
