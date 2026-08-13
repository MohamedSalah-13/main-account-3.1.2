package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

@FunctionalInterface
public interface InvoiceNumberAllocator {
    int next(DocumentType documentType) throws DaoException;
}
