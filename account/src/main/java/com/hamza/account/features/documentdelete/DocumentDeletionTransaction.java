package com.hamza.account.features.documentdelete;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/** The transaction a delete runs in; a test runs the work directly. */
@FunctionalInterface
public interface DocumentDeletionTransaction {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static DocumentDeletionTransaction jdbc() {
        return new DocumentDeletionTransaction() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }
}
