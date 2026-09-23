package com.hamza.account.features.profitloss.yearly;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** What each month's sales were made of, and the years there is anything to report on. */
public interface YearlyBreakdownRepository {

    /** One entry per month that has any document or expense in it; a quiet month is simply absent. */
    List<MonthBreakdown> breakdown(int year) throws DaoException;

    /** Every year a document was written in, newest first. */
    List<Integer> years() throws DaoException;
}
