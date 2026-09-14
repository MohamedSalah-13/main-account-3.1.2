package com.hamza.account.features.unitprices;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/** The save's transaction boundary, replaceable in a test that has no database. */
@FunctionalInterface
public interface UnitPriceTransactionExecutor {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static UnitPriceTransactionExecutor jdbc() {
        return new UnitPriceTransactionExecutor() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static UnitPriceTransactionExecutor direct() {
        return new UnitPriceTransactionExecutor() {
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
