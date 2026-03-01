package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Purchase;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record PurchaseService(DaoFactory daoFactory) {

    public List<Purchase> fetchByInvoiceNumber(int invoiceNumber) throws DaoException {
//        return getAllPurchases().stream().filter(purchase -> purchase.getInvoiceNumber() == invoiceNumber).toList();
        return daoFactory.purchaseDao().loadAllById(invoiceNumber);

    }

    public List<Purchase> findBetweenTwoInvoiceNumber(int firstId, int lastId) throws DaoException {
//        return getAllPurchases().stream().filter(purchase -> purchase.getInvoiceNumber() >= firstId && purchase.getInvoiceNumber() <= lastId).toList();
        return daoFactory.purchaseDao().loadBetweenTwoInvoiceNumber(firstId, lastId);
    }

    public List<Purchase> findByNumItem(int numItem) throws DaoException {
        return daoFactory.purchaseDao().findByNumItem(numItem);
    }

}
