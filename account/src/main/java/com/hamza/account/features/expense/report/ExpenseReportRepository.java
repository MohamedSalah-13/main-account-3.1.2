package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** What the expense reports read, as one seam, so {@link ExpenseReportService} can be tested in memory. */
public interface ExpenseReportRepository {

    List<ExpenseReportRows.HeadingTotal> headingTotals(ExpenseFilter filter) throws DaoException;

    List<ExpenseReportRows.HeadingDay> headingDays(ExpenseFilter filter) throws DaoException;

    List<ExpenseReportRows.Day> days(ExpenseFilter filter) throws DaoException;

    List<ExpenseReportRows.DimensionTotal> dimension(ExpenseFilter filter, ExpenseDimension dimension)
            throws DaoException;

    /** Net sales per day, both days included. */
    List<ExpenseReportRows.Day> netSalesDays(LocalDate from, LocalDate to) throws DaoException;
}
