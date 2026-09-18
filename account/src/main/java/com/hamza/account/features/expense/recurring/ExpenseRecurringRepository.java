package com.hamza.account.features.expense.recurring;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything the recurring-expense screen and its reminder read and write, as one seam. */
public interface ExpenseRecurringRepository {

    /** Every template, stopped ones included. */
    List<ExpenseRecurring> all() throws DaoException;

    /** The templates that can fall due. */
    List<ExpenseRecurring> active() throws DaoException;

    ExpenseRecurring find(int id) throws DaoException;

    /**
     * The period starts already recorded against each active template, filed by
     * {@link ExpenseFrequency#periodStart}.
     *
     * @param since the earliest expense date worth reading - the reminder looks back two periods, not
     *              over the whole history
     */
    Map<Integer, Set<LocalDate>> recordedPeriods(LocalDate since) throws DaoException;

    /** How many expenses were recorded from this template. */
    int recordedCount(int id) throws DaoException;

    int insert(ExpenseRecurringDraft draft, int userId) throws DaoException;

    int update(ExpenseRecurringDraft draft) throws DaoException;

    int delete(int id) throws DaoException;
}
