package com.hamza.account.document;

import com.hamza.controlsfx.database.DaoException;

/** Shared persistence invariant for the four invoice header tables. */
public final class DocumentWriteGuard {

    private DocumentWriteGuard() {
    }

    public static void requireSingleHeaderRow(int affectedRows, DocumentType type)
            throws DaoException {
        if (affectedRows != 1) {
            throw new DaoException("تعذر حفظ " + type.label()
                    + "؛ يجب أن تؤثر العملية في رأس فاتورة واحد فقط");
        }
    }
}
