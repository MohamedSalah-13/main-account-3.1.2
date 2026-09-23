package com.hamza.account.features.currency;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;

/**
 * The transaction boundary {@link CurrencyService} writes inside: {@link TransactionTemplate} in the
 * application, the work run directly in a unit test. The shape {@code ExpenseTransactions} set.
 * <p>
 * What a direct run cannot prove is written down rather than implied: that the row locks keep a rate
 * from being recorded while the base moves. That needs two real connections, and
 * {@code CurrencyDatabaseAcceptanceTest} is what holds it.
 */
public interface CurrencyTransactions {

    <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException;

    static CurrencyTransactions jdbc() {
        return new CurrencyTransactions() {
            @Override
            public <T> T execute(TransactionTemplate.TransactionalSupplier<T> work) throws DaoException {
                return TransactionTemplate.execute(work);
            }
        };
    }

    static CurrencyTransactions direct() {
        return new CurrencyTransactions() {
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
