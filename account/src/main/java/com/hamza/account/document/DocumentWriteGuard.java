package com.hamza.account.document;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;

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
}
