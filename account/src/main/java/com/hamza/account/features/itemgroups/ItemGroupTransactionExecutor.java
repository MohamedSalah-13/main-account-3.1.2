package com.hamza.account.features.itemgroups;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

@FunctionalInterface
public interface ItemGroupTransactionExecutor {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static ItemGroupTransactionExecutor jdbc() {
        return new ItemGroupTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static ItemGroupTransactionExecutor direct() {
        return new ItemGroupTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
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
