package com.hamza.account.features.expense;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/**
 * The transaction boundary {@link ExpenseService} writes inside: {@link TransactionTemplate} in the
 * application, the work run directly in a unit test. The shape {@code InvoiceTransactionExecutor} set.
 * <p>
 * What a direct run cannot prove is written down rather than implied: that a refused line of a batch
 * takes the lines before it back. That needs a real transaction, and is the acceptance test's.
 */
public interface ExpenseTransactions {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static ExpenseTransactions jdbc() {
        return new ExpenseTransactions() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static ExpenseTransactions direct() {
        return new ExpenseTransactions() {
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
