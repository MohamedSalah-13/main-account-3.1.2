package com.hamza.account.features.invoice;

import com.hamza.controlsfx.database.DaoException;

@FunctionalInterface
public interface InvoiceLookup<T> {
    T find(String name) throws DaoException;
}
