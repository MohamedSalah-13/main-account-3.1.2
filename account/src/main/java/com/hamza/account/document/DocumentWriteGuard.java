package com.hamza.account.document;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDateTime;

/** Shared persistence invariant for the four invoice header tables. */
public final class DocumentWriteGuard {

    private DocumentWriteGuard() {
    }

    public static void requireSingleHeaderRow(int affectedRows, DocumentType type)
            throws DaoException {
        if (affectedRows != 1) {
            throw new DaoException(LanguageManager.getInstance()
                    .getString("document.error.single.header.row", type.label()));
        }
    }

    /**
     * An update is a compare-and-swap against the timestamp the editor loaded. Zero
     * means another machine changed or deleted the document first; more than one is a
     * broken persistence invariant rather than an ordinary user conflict.
     */
    public static void requireOptimisticUpdate(int affectedRows, DocumentType type)
            throws DaoException {
        if (affectedRows == 0) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("document.error.concurrent.update", type.label()));
        }
        requireSingleHeaderRow(affectedRows, type);
    }

    /** Refuses an edit that did not originate from a version read from the database. */
    public static LocalDateTime requireEditVersion(LocalDateTime expectedVersion,
                                                   DocumentType type)
            throws DaoException {
        if (expectedVersion == null) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("document.error.concurrent.update", type.label()));
        }
        return expectedVersion;
    }
}
