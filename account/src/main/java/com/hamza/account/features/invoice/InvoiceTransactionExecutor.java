package com.hamza.account.features.invoice;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/** Injectable transaction boundary: real JDBC in production, direct execution in unit tests. */
public interface InvoiceTransactionExecutor {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static InvoiceTransactionExecutor jdbc() {
        return new InvoiceTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work)
                    throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static InvoiceTransactionExecutor direct() {
        return new InvoiceTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work)
                    throws DaoException {
                try {
                    return work.get();
                } catch (DaoException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DaoException(e);
                }
            }
        };
    }
}
