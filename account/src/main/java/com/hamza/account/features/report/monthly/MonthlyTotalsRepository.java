package com.hamza.account.features.report.monthly;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** Where the monthly totals come from - a seam so the service is tested without a database. */
public interface MonthlyTotalsRepository {

    /** Every day holding a document of the side, oldest first. */
    List<DayFigures> days(MonthlySide side) throws DaoException;
}
