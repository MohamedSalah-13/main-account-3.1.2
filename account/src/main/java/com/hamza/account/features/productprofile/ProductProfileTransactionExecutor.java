package com.hamza.account.features.productprofile;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/** Injectable transaction boundary: JDBC in production and direct execution in unit tests. */
@FunctionalInterface
public interface ProductProfileTransactionExecutor {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static ProductProfileTransactionExecutor jdbc() {
        return new ProductProfileTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static ProductProfileTransactionExecutor direct() {
        return new ProductProfileTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                try {
                    return work.get();
                } catch (DaoException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new DaoException(exception);
                }
            }
        };
    }
}
