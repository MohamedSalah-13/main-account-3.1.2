package com.hamza.account.features.stockledger;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

/**
 * Writes to {@code stock_movements}. Append-only for now, with one deliberate exception
 * documented on {@link #deleteByReference}.
 * <p>
 * There is no {@code map()}/{@code loadAll()} here on purpose: nothing reads this table
 * yet (see {@code docs/erp-roadmap.md} §8), so this is a pure write path, following the
 * same manual-{@code PreparedStatement} shape as {@code StockCountDao.insertLines} rather
 * than the generic {@code AbstractDao<T>} entity machinery the four invoice-line DAOs use
 * for their read side.
 */
public class StockMovementDao extends AbstractDao<StockMovement> {

    public void insertBatch(List<StockMovement> movements) throws DaoException {
        if (movements.isEmpty()) {
            return;
        }
        String query = """
                INSERT INTO stock_movements
                    (item_id, stock_id, movement_date, movement_type, quantity_in, quantity_out,
                     unit_id, unit_value, reference_type, reference_id, user_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                for (StockMovement movement : movements) {
                    statement.setInt(1, movement.itemId());
                    statement.setInt(2, movement.stockId());
                    statement.setObject(3, movement.movementDate());
                    statement.setString(4, movement.movementType().name());
                    statement.setDouble(5, movement.quantityIn());
                    statement.setDouble(6, movement.quantityOut());
                    statement.setInt(7, movement.unitId());
                    statement.setDouble(8, movement.unitValue());
                    statement.setString(9, movement.referenceType());
                    statement.setLong(10, movement.referenceId());
                    if (movement.userId() == null) {
                        statement.setNull(11, Types.INTEGER);
                    } else {
                        statement.setInt(11, movement.userId());
                    }
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /**
     * Removes every movement written for one document, ahead of re-inserting the
     * current set on an edit.
     * <p>
     * This is the one place the ledger is not append-only yet, and it is deliberate,
     * not an oversight: nothing reads {@code stock_movements} today (§8.4 is the dual
     * write, §8.6 is the day something reads it), so there is no report or balance that
     * "delete and re-insert" could show inconsistently mid-edit. Once the ledger becomes
     * the source of truth, an edit must instead write a reversal alongside the new
     * lines - never delete a posted row - per {@code docs/erp-roadmap.md} ق-11. That
     * change belongs to §8.6, together with the cutover, not to this dual-write phase.
     */
    public void deleteByReference(String referenceType, long referenceId) throws DaoException {
        executeUpdate("DELETE FROM stock_movements WHERE reference_type = ? AND reference_id = ?",
                referenceType, referenceId);
    }
}
