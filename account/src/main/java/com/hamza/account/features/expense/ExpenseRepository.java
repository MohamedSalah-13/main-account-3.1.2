package com.hamza.account.features.expense;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/**
 * Everything the expenses screens read and write, as one seam - so {@link ExpenseService} can be tested
 * against a list in memory.
 */
public interface ExpenseRepository {

    /** One page more one row, in the filter's order. */
    List<ExpenseRow> search(ExpenseFilter filter) throws DaoException;

    /** The count and total over the whole filtered set, with its top heading; no previous period. */
    ExpenseSummary summarize(ExpenseFilter filter) throws DaoException;

    /** One expense, or {@code null}. */
    ExpenseRow find(int id) throws DaoException;

    List<ExpenseUserOption> users() throws DaoException;

    List<String> payees(String prefix, int limit) throws DaoException;

    /**
     * Writes a new expense.
     *
     * @param employeeId  the employee a payment is for, or {@code null}
     * @param shiftId     the shift the gate answered with, or {@code null}
     * @param recurringId the template it was recorded from, or {@code null} for a hand-entered expense
     * @return the generated code
     */
    int insert(ExpenseEntry entry, Integer employeeId, Integer shiftId, int userId, Integer recurringId)
            throws DaoException;

    int update(ExpenseEntry entry) throws DaoException;

    int delete(int id) throws DaoException;
}
