package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

/** Where the two reports are read - a seam, so {@link TierReportService} is tested without MySQL. */
public interface TierReportRepository {

    TierReports.MissingPage missing(List<Integer> activeTiers, String text, int limit, int offset) throws DaoException;

    TierReports.BelowListPage belowList(LocalDate from, LocalDate to, String text, int limit, int offset)
            throws DaoException;
}
