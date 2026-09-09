package com.hamza.account.features.totals;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** The read side of the totals reports, so the service can be tested without a database. */
public interface TotalsReportRepository {

    List<TotalsReportRow> run(DocumentTableSpec spec, DocumentTableSpec.Report report,
                              TotalsSearchCriteria criteria) throws DaoException;
}
