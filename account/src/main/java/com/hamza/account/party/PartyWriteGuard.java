package com.hamza.account.party;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

/** Optimistic-write rules shared by customer and supplier records. */
public final class PartyWriteGuard {

    private PartyWriteGuard() {
    }

    public static Object[] withVersion(Object[] values, LocalDateTime expectedVersion)
            throws DaoException {
        if (expectedVersion == null) {
            throw conflict();
        }
        Object[] versioned = Arrays.copyOf(values, values.length + 1);
        versioned[values.length] = Timestamp.valueOf(expectedVersion);
        return versioned;
    }

    public static void requireUpdated(int affectedRows) throws DaoException {
        if (affectedRows == 0) {
            throw conflict();
        }
        if (affectedRows != 1) {
            throw new DaoException(LanguageManager.getInstance()
                    .getString("party.error.single.row"));
        }
    }

    private static BusinessRuleException conflict() {
        return new BusinessRuleException(LanguageManager.getInstance()
                .getString("party.error.concurrent.update"));
    }
}
