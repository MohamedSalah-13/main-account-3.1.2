package com.hamza.account.features.expense.budget;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** Everything the budget screen and the budget report read and write, as one seam. */
public interface ExpenseBudgetRepository {

    List<ExpenseBudget> byYear(int year) throws DaoException;

    /** Every budget that touches the period, monthly and yearly together. */
    List<ExpenseBudget> forPeriod(LocalDate from, LocalDate to) throws DaoException;

    ExpenseBudget find(int id) throws DaoException;

    /** Whether the heading already has a budget for that period, ignoring {@code exceptId}. */
    boolean taken(int headingId, int year, Integer month, int exceptId) throws DaoException;

    List<Integer> years() throws DaoException;

    int insert(ExpenseBudgetDraft draft, int userId) throws DaoException;

    int update(ExpenseBudgetDraft draft) throws DaoException;

    int delete(int id) throws DaoException;
}
