package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the delegates did, read from the documents. An interface so the service is testable without MySQL. */
public interface DelegateActivityRepository {

    /** One row per delegate for the period, both ends included. */
    List<DelegateActivity> activity(LocalDate from, LocalDate to) throws DaoException;

    /** Cash collected on customers' accounts in the period that names no delegate. */
    BigDecimal unattributedCollections(LocalDate from, LocalDate to) throws DaoException;

    /**
     * Writes the delegate of a collection that has just been entered; see
     * {@link DelegateActivityQuery#ATTRIBUTE_COLLECTION_SQL}. Joins the caller's transaction.
     *
     * @return the rows the statement matched: 0 for a movement that carried no cash or already
     *         names a delegate. 1 does not promise a delegate was found - a customer with no
     *         default leaves the column NULL, which is a true answer
     */
    int attributeCollection(long accountNumber) throws DaoException;
}
