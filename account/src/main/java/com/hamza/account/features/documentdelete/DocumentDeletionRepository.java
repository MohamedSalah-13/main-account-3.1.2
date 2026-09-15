package com.hamza.account.features.documentdelete;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** What deleting a document needs from the database, so the order of it can be tested without one. */
public interface DocumentDeletionRepository {

    /** Refuses the whole batch when any of the documents falls inside a closed accounting period. */
    void requirePeriodOpen(DocumentType type, List<Integer> ids) throws DaoException;

    /** Refuses the whole batch when any of the invoices has been returned against. */
    void requireNoReturns(DocumentType type, List<Integer> ids) throws DaoException;

    /**
     * Removes the documents with everything that has to go with them - their stock movements, the
     * reversal of their cash in the shift journal, the announcement to the other tills - and answers
     * how many headers were actually removed. Runs inside the caller's transaction.
     */
    int deleteDocuments(DocumentType type, List<Integer> ids, String correctionReason) throws DaoException;
}
