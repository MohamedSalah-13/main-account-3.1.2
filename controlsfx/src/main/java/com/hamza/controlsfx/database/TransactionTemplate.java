package com.hamza.controlsfx.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Runs application-service work on the same connection used by all participating DAOs. */
public final class TransactionTemplate {

    private TransactionTemplate() {
    }

    public static <T> T execute(TransactionalSupplier<T> work) throws DaoException {
        Connection transaction = null;
        try {
            transaction = ConnectionManager.beginTransaction();
            T result = work.get();
            if (transaction != null) {
                transaction.commit();
            }
            return result;
        } catch (DaoException e) {
            rollback(transaction, e);
            throw e;
        } catch (Exception e) {
            rollback(transaction, e);
            throw new DaoException("فشلت المعاملة وتم التراجع عن جميع التغييرات", e);
        } finally {
            ConnectionManager.endTransaction(transaction);
        }
    }

    private static void rollback(Connection transaction, Exception original)
            throws DaoException {
        if (transaction == null) {
            return;
        }
        try {
            transaction.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
            throw new DaoException("فشلت المعاملة وفشل التراجع عنها", original);
        }
    }

    @FunctionalInterface
    public interface TransactionalSupplier<T> {
        T get() throws Exception;
    }
}
