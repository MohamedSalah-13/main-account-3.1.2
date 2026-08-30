package com.hamza.account.model.dao;

import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.account.treasury.TreasuryType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Reads {@code treasury_current_balance} - the only place in the application that
 * answers what a treasury holds.
 * <p>
 * Read-only by design: there is no row to write. The balance is derived from the
 * documents, the opening balance, the deposits and the transfers every time it is
 * asked for, so nothing can drift out of step with them and editing an invoice
 * corrects it with no reversal code at all (docs/treasury-plan.md §2).
 */
public class TreasuryCurrentBalanceDao extends AbstractDao<TreasuryBalanceSummary> {

    @Override
    public List<TreasuryBalanceSummary> loadAll() throws DaoException {
        return queryForObjects(TreasuryStatements.SELECT_ALL_BALANCES, this::map);
    }

    /** The treasuries a picker may offer - a closed one keeps its history and hides. */
    public List<TreasuryBalanceSummary> loadActive() throws DaoException {
        return queryForObjects(TreasuryStatements.SELECT_ACTIVE_BALANCES, this::map);
    }

    @Override
    public TreasuryBalanceSummary getDataById(int id) throws DaoException {
        return queryForObject(TreasuryStatements.SELECT_BALANCE_BY_ID, this::map, id);
    }

    /**
     * Takes a row lock on the treasury and answers what it holds.
     * <p>
     * For a caller that is about to take money out. The balance is derived, so
     * checking it and then inserting is a read-then-write on a number nothing holds
     * still - see {@code TreasuryStatements.LOCK_TREASURY}. Only meaningful inside a
     * transaction, which is where the services call it from.
     */
    public TreasuryBalanceSummary lockAndRead(int treasuryId) throws DaoException {
        withConnection(connection -> {
            try (java.sql.PreparedStatement statement =
                         connection.prepareStatement(TreasuryStatements.LOCK_TREASURY)) {
                statement.setInt(1, treasuryId);
                statement.executeQuery();
            }
            return null;
        });
        return getDataById(treasuryId);
    }

    @Override
    public TreasuryBalanceSummary map(ResultSet rs) throws DaoException {
        try {
            return new TreasuryBalanceSummary(
                    rs.getInt("id"),
                    rs.getString("t_name"),
                    TreasuryType.fromCode(rs.getString("treasury_type")),
                    rs.getBoolean("is_active"),
                    rs.getInt("sort_order"),
                    amount(rs, "fee_percent"),
                    amount(rs, "opening"),
                    amount(rs, "total_in"),
                    amount(rs, "total_out"),
                    amount(rs, "balance"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /** A treasury with no movement at all leaves the aggregate columns null. */
    private BigDecimal amount(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }
}
