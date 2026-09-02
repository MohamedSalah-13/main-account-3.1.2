package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Read-only accounting checks across the close snapshot, journal and live source rows. */
final class ShiftReconciliationDao extends AbstractDao<Object> {

    ShiftReconciliationResult reconcile(int shiftId) throws DaoException {
        SnapshotState snapshot = readSnapshot(shiftId);
        int sourceMismatches = countSourceMismatches(shiftId);
        int duplicateCreates = countDuplicateCreates(shiftId);
        int invalidReasons = countInvalidReasons(shiftId);
        int postCloseEntries = snapshot == null ? 0 : snapshot.postCloseEntries();

        boolean present = snapshot != null;
        boolean complete = present && snapshot.ledgerComplete();
        BigDecimal snapshotIncome = present ? snapshot.snapshotIncome() : BigDecimal.ZERO;
        BigDecimal snapshotOutput = present ? snapshot.snapshotOutput() : BigDecimal.ZERO;
        BigDecimal ledgerIncome = present ? snapshot.ledgerIncome() : sumLedger(shiftId, "income_delta");
        BigDecimal ledgerOutput = present ? snapshot.ledgerOutput() : sumLedger(shiftId, "output_delta");
        ShiftReconciliationStatus status = ShiftReconciliationResult.classify(
                present, complete, snapshotIncome, snapshotOutput, ledgerIncome, ledgerOutput,
                sourceMismatches, duplicateCreates, invalidReasons, postCloseEntries);
        return new ShiftReconciliationResult(shiftId, status, present, complete,
                snapshotIncome, snapshotOutput, ledgerIncome, ledgerOutput,
                sourceMismatches, duplicateCreates, invalidReasons, postCloseEntries);
    }

    private SnapshotState readSnapshot(int shiftId) throws DaoException {
        return (SnapshotState) queryForObject("""
                SELECT s.ledger_complete, s.total_cash_in, s.total_cash_out,
                       COALESCE(SUM(CASE WHEN l.id<=s.ledger_last_id THEN l.income_delta ELSE 0 END),0) AS ledger_income,
                       COALESCE(SUM(CASE WHEN l.id<=s.ledger_last_id THEN l.output_delta ELSE 0 END),0) AS ledger_output,
                       COALESCE(SUM(CASE WHEN s.ledger_complete AND l.id>s.ledger_last_id THEN 1 ELSE 0 END),0)
                           AS post_close_entries
                FROM shift_close_snapshots s
                LEFT JOIN shift_cash_ledger l ON l.shift_id=s.shift_id
                WHERE s.shift_id=?
                GROUP BY s.shift_id, s.ledger_complete, s.total_cash_in, s.total_cash_out, s.ledger_last_id
                """, this::mapSnapshot, shiftId);
    }

    private SnapshotState mapSnapshot(ResultSet result) throws DaoException {
        try {
            return new SnapshotState(result.getBoolean("ledger_complete"),
                    result.getBigDecimal("total_cash_in"), result.getBigDecimal("total_cash_out"),
                    result.getBigDecimal("ledger_income"), result.getBigDecimal("ledger_output"),
                    result.getInt("post_close_entries"));
        } catch (SQLException e) {
            throw new DaoException("Could not map shift reconciliation snapshot", e);
        }
    }

    private int countSourceMismatches(int shiftId) throws DaoException {
        return scalarInt("""
                WITH touched AS (
                    SELECT DISTINCT source_type, source_id, treasury_id
                    FROM shift_cash_ledger
                    WHERE shift_id=? OR origin_shift_id=?
                ), journal_state AS (
                    SELECT t.source_type, t.source_id, t.treasury_id,
                           COALESCE(SUM(l.income_delta),0) AS income,
                           COALESCE(SUM(l.output_delta),0) AS output
                    FROM touched t
                    LEFT JOIN shift_cash_ledger l
                      ON l.source_type=t.source_type AND l.source_id=t.source_id
                     AND l.treasury_id=t.treasury_id
                    GROUP BY t.source_type, t.source_id, t.treasury_id
                ), live_state AS (
                    SELECT t.source_type, t.source_id, t.treasury_id,
                           COALESCE(SUM(b.income),0) AS income,
                           COALESCE(SUM(b.output),0) AS output
                    FROM touched t
                    LEFT JOIN treasury_balance b
                      ON b.source_type=t.source_type AND b.id_no=t.source_id
                     AND b.treasury_id=t.treasury_id
                    GROUP BY t.source_type, t.source_id, t.treasury_id
                )
                SELECT COUNT(*)
                FROM journal_state j
                JOIN live_state v USING (source_type, source_id, treasury_id)
                WHERE ABS(j.income-v.income)>0.0001 OR ABS(j.output-v.output)>0.0001
                """, shiftId, shiftId);
    }

    private int countDuplicateCreates(int shiftId) throws DaoException {
        return scalarInt("""
                SELECT COUNT(*) FROM (
                    SELECT source_type, source_id, treasury_id
                    FROM shift_cash_ledger
                    WHERE shift_id=? OR origin_shift_id=?
                    GROUP BY source_type, source_id, treasury_id
                    HAVING SUM(action_type='CREATE')>1
                ) duplicates
                """, shiftId, shiftId);
    }

    private int countInvalidReasons(int shiftId) throws DaoException {
        return scalarInt("""
                SELECT COUNT(*) FROM shift_cash_ledger
                WHERE (shift_id=? OR origin_shift_id=?) AND action_type<>'CREATE'
                  AND (reason IS NULL OR TRIM(reason)='')
                """, shiftId, shiftId);
    }

    private BigDecimal sumLedger(int shiftId, String column) throws DaoException {
        return (BigDecimal) queryForObject("SELECT COALESCE(SUM(" + column + "),0) FROM shift_cash_ledger WHERE shift_id=?",
                result -> {
                    try {
                        return result.getBigDecimal(1);
                    } catch (SQLException e) {
                        throw new DaoException("Could not sum shift journal", e);
                    }
                }, shiftId);
    }

    private int scalarInt(String sql, Object... parameters) throws DaoException {
        Integer value = (Integer) queryForObject(sql, result -> {
            try {
                return result.getInt(1);
            } catch (SQLException e) {
                throw new DaoException("Could not read shift reconciliation count", e);
            }
        }, parameters);
        return value == null ? 0 : value;
    }

    private record SnapshotState(boolean ledgerComplete, BigDecimal snapshotIncome,
                                 BigDecimal snapshotOutput, BigDecimal ledgerIncome,
                                 BigDecimal ledgerOutput, int postCloseEntries) {
    }

    @Override public java.util.List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(ResultSet rs) { throw new UnsupportedOperationException(); }
}
