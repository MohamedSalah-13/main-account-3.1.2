package com.hamza.account.features.shift;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.OptionalInt;

/** Writes the shift foreign key after a business row is saved, in the same transaction. */
public final class ShiftAttributionWriter {
    private final boolean enabled;

    private ShiftAttributionWriter(boolean enabled) {
        this.enabled = enabled;
    }

    public static ShiftAttributionWriter jdbc() {
        return new ShiftAttributionWriter(true);
    }

    public static ShiftAttributionWriter disabled() {
        return new ShiftAttributionWriter(false);
    }

    public void assignDocument(DocumentType type, int documentId, OptionalInt shiftId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        assign(spec.table(), spec.key(), documentId, "shift_id", shiftId);
    }

    public void assignParty(PartyKind kind, int movementId, OptionalInt shiftId) throws DaoException {
        PartyLedgerSpec spec = PartyLedgerSpec.of(kind);
        assign(spec.table(), PartyLedgerSpec.KEY, movementId, "shift_id", shiftId);
    }

    private void assign(String table, String key, int id, String column, OptionalInt shiftId) throws DaoException {
        if (!enabled) return;
        String sql = "UPDATE " + table + " SET " + column + "=? WHERE " + key + "=?";
        java.sql.Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (shiftId.isPresent()) statement.setInt(1, shiftId.getAsInt());
                else statement.setNull(1, Types.INTEGER);
                statement.setInt(2, id);
                if (statement.executeUpdate() != 1) throw new DaoException("Cash movement shift was not assigned");
            }
        } catch (SQLException e) {
            throw new DaoException("Could not assign the cash movement to a shift", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }
}
