package com.hamza.account.features.delegate.report;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.List;

public interface DelegateDetailRepository {

    List<DelegateDetailRow> breakdown(DelegateBreakdown breakdown, DelegateDetailFilter filter) throws DaoException;

    /** What was taken off whole invoices, then off whole returns. */
    BigDecimal[] headerDiscounts(DelegateDetailFilter filter) throws DaoException;

    List<DelegateCollectionRow> collections(DelegateDetailFilter filter) throws DaoException;
}
