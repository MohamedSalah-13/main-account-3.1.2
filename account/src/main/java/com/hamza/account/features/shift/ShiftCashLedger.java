package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.util.OptionalInt;

/**
 * Append-only journal for the cash portion of shift-owned business events.
 * Updates write deltas and deletes write exact reversals; this class exposes no
 * update or delete operation for journal rows.
 */
public final class ShiftCashLedger extends AbstractDao<Object> {
    private final boolean enabled;

    private ShiftCashLedger(boolean enabled) {
        this.enabled = enabled;
    }

    public static ShiftCashLedger jdbc() {
        return new ShiftCashLedger(true);
    }

    public static ShiftCashLedger disabled() {
        return new ShiftCashLedger(false);
    }

    public void created(OptionalInt shiftId, int actorUserId, ShiftCashEffect effect) throws DaoException {
        append(shiftId, actorUserId, ShiftLedgerAction.CREATE, effect, effect.income(), effect.output());
    }

    public void updated(OptionalInt oldShift, OptionalInt newShift, int actorUserId,
                        ShiftCashEffect before, ShiftCashEffect after) throws DaoException {
        updated(oldShift, newShift, actorUserId, before, after, null);
    }

    public void updated(OptionalInt oldShift, OptionalInt newShift, int actorUserId,
                        ShiftCashEffect before, ShiftCashEffect after, String reason) throws DaoException {
        if ((!enabled) || (oldShift.isEmpty() && newShift.isEmpty())) return;
        String correctionReason = requireCorrectionReason(reason);
        ensureBaseline(actorUserId, before, oldShift.isPresent() ? oldShift : newShift);
        boolean sameBucket = before.source() == after.source()
                && before.treasuryId() == after.treasuryId()
                && oldShift.equals(newShift);
        if (sameBucket) {
            append(newShift, actorUserId, ShiftLedgerAction.UPDATE, after,
                    after.income().subtract(before.income()),
                    after.output().subtract(before.output()), correctionReason, before.originalShiftId());
            return;
        }
        append(oldShift, actorUserId, ShiftLedgerAction.UPDATE, before,
                before.income().negate(), before.output().negate(), correctionReason, before.originalShiftId());
        append(newShift, actorUserId, ShiftLedgerAction.UPDATE, after,
                after.income(), after.output(), correctionReason, before.originalShiftId());
    }

    public void deleted(OptionalInt shiftId, int actorUserId, ShiftCashEffect effect) throws DaoException {
        deleted(shiftId, actorUserId, effect, null);
    }

    public void deleted(OptionalInt shiftId, int actorUserId, ShiftCashEffect effect, String reason) throws DaoException {
        if (!enabled || shiftId == null || shiftId.isEmpty()) return;
        String correctionReason = requireCorrectionReason(reason);
        ensureBaseline(actorUserId, effect, shiftId);
        append(shiftId, actorUserId, ShiftLedgerAction.DELETE, effect,
                effect.income().negate(), effect.output().negate(), correctionReason, effect.originalShiftId());
    }

    /**
     * Records what a document was already worth, so the deltas that follow add up to its
     * live value - the per-document invariant {@code ShiftReconciliationDao} checks.
     *
     * <p>Where that baseline is filed is a question about money, not bookkeeping. A document
     * created inside a shift belongs to that shift, and {@code originalShiftId} says which.
     * A document created <b>before shifts were switched on</b> belongs to none, and the
     * {@code fallbackShift} - whichever shift happens to be open now, because somebody is
     * editing it - is the one answer that is certainly wrong: measured on a real database,
     * editing an August invoice inside today's shift filed its whole 505 under today, so the
     * drawer was expected to hold cash collected a month earlier and would have counted short
     * by all of it.
     *
     * <p>So an unattributed baseline is written with no shift at all (V53). It still balances
     * the document, and every per-shift read filters {@code shift_id = ?} and passes over it.
     */
    private void ensureBaseline(int actorUserId, ShiftCashEffect effect, OptionalInt fallbackShift)
            throws DaoException {
        if (!enabled || hasCreate(effect)) return;
        if (effect.originalShiftId() == null) {
            appendUnattributedBaseline(actorUserId, effect);
            return;
        }
        created(OptionalInt.of(effect.originalShiftId()), actorUserId, effect);
    }

    /**
     * The baseline for cash that moved before any shift existed. Deliberately not routed
     * through {@link #append}, which treats an absent shift as "shifts are off, record
     * nothing" - the opposite of what this row is for.
     */
    private void appendUnattributedBaseline(int actorUserId, ShiftCashEffect effect) throws DaoException {
        int rows = executeUpdate("""
                INSERT INTO shift_cash_ledger
                    (shift_id, origin_shift_id, treasury_id, actor_user_id, source_type, source_id,
                     action_type, movement_label, income_delta, output_delta, reason)
                VALUES (NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """, effect.treasuryId(), actorUserId, effect.source().code(), effect.sourceId(),
                ShiftLedgerAction.CREATE.name(), effect.source().label().name(),
                effect.income(), effect.output());
        if (rows != 1) throw new DaoException("Shift cash journal row was not appended");
    }

    private boolean hasCreate(ShiftCashEffect effect) throws DaoException {
        String sql = "SELECT COUNT(*) FROM shift_cash_ledger WHERE source_type=? AND source_id=? "
                + "AND treasury_id=? AND action_type='CREATE'";
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setInt(1, effect.source().code());
                statement.setInt(2, effect.sourceId());
                statement.setInt(3, effect.treasuryId());
                try (var result = statement.executeQuery()) {
                    return result.next() && result.getInt(1) > 0;
                }
            } catch (java.sql.SQLException e) {
                throw new DaoException("Could not inspect shift cash journal", e);
            }
        });
    }

    private void append(OptionalInt shiftId, int actorUserId, ShiftLedgerAction action,
                        ShiftCashEffect effect, BigDecimal income, BigDecimal output) throws DaoException {
        append(shiftId, actorUserId, action, effect, income, output, null);
    }

    private void append(OptionalInt shiftId, int actorUserId, ShiftLedgerAction action,
                        ShiftCashEffect effect, BigDecimal income, BigDecimal output, String reason) throws DaoException {
        append(shiftId, actorUserId, action, effect, income, output, reason, null);
    }

    private void append(OptionalInt shiftId, int actorUserId, ShiftLedgerAction action,
                        ShiftCashEffect effect, BigDecimal income, BigDecimal output,
                        String reason, Integer originShiftId) throws DaoException {
        if (!enabled || shiftId == null || shiftId.isEmpty()) return;
        // A zero CREATE is still a baseline marker. Without it, a later edit from
        // zero to cash would be counted once from the live row and once as a delta.
        if (action != ShiftLedgerAction.CREATE && income.signum() == 0 && output.signum() == 0) return;
        int rows = executeUpdate("""
                INSERT INTO shift_cash_ledger
                    (shift_id, origin_shift_id, treasury_id, actor_user_id, source_type, source_id,
                     action_type, movement_label, income_delta, output_delta, reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, shiftId.getAsInt(), originShiftId, effect.treasuryId(), actorUserId,
                effect.source().code(), effect.sourceId(), action.name(),
                effect.source().label().name(), income, output, reason);
        if (rows != 1) throw new DaoException("Shift cash journal row was not appended");
    }

    private static String requireCorrectionReason(String reason) throws DaoException {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty()) {
            throw new DaoException("A correction reason is required for shift cash updates and deletions");
        }
        if (normalized.length() > 500) {
            throw new DaoException("A shift correction reason cannot exceed 500 characters");
        }
        return normalized;
    }

    @Override public java.util.List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(java.sql.ResultSet rs) { throw new UnsupportedOperationException(); }
}
