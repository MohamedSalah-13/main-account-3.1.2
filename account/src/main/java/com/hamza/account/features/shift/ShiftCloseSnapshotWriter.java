package com.hamza.account.features.shift;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Timestamp;

/** Writes the one immutable accounting snapshot created when a shift closes. */
public final class ShiftCloseSnapshotWriter extends AbstractDao<Object> {

    public void append(UserShift shift, int closedByUserId) throws DaoException {
        int rows = executeUpdate("""
                INSERT INTO shift_close_snapshots (
                    shift_id, closed_by_user_id, shift_status, open_time, close_time,
                    open_balance, actual_balance, expected_balance, difference_amount,
                    total_sales, total_sales_returns, total_expenses, total_deposits,
                    total_withdrawals, total_cash_in, total_cash_out, invoices_count,
                    ledger_last_id, ledger_complete)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        COALESCE((SELECT MAX(id) FROM shift_cash_ledger WHERE shift_id=?),0), TRUE)
                """, shift.getId(), closedByUserId, shift.getStatus().name(),
                Timestamp.valueOf(shift.getOpenTime()), Timestamp.valueOf(shift.getCloseTime()),
                shift.getOpenBalance(), shift.getCloseBalance(), shift.getExpectedBalance(),
                shift.getDifference(), shift.getTotalSales(), shift.getTotalSalesReturns(),
                shift.getTotalExpenses(), shift.getTotalDeposits(), shift.getTotalWithdrawals(),
                shift.getTotalCashIn(), shift.getTotalCashOut(), shift.getInvoicesCount(), shift.getId());
        if (rows != 1) throw new DaoException("Shift close snapshot was not appended");
    }

    @Override public java.util.List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(java.sql.ResultSet rs) { throw new UnsupportedOperationException(); }
}
