package com.hamza.account.features.treasury;

import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** {@link WalletFeeRepository} over {@code expenses_details}; every statement is in {@link TreasuryStatements}. */
public final class JdbcWalletFeeRepository extends AbstractDao<Object> implements WalletFeeRepository {

    @Override
    public StoredFee lockFor(WalletFeeSource source) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.LOCK_WALLET_FEE_FOR_SOURCE)) {
                statement.setInt(1, source.kind().code());
                statement.setLong(2, source.id());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    int shiftId = rs.getInt("shift_id");
                    Integer shift = rs.wasNull() ? null : shiftId;
                    return new StoredFee(rs.getInt("id"), rs.getDate("date").toLocalDate(),
                            rs.getBigDecimal("amount"), rs.getInt("treasury_id"), shift);
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read the wallet fee of a movement", e);
            }
        });
    }

    @Override
    public int insert(WalletFeeSource source, int headingId, LocalDate date, BigDecimal amount, String notes,
                      int treasuryId, int userId, Integer shiftId) throws DaoException {
        return insertReturningId(TreasuryStatements.INSERT_WALLET_FEE, headingId, Date.valueOf(date), amount,
                notes, treasuryId, userId, shiftId, source.kind().code(), (long) source.id());
    }

    @Override
    public int update(int expenseId, LocalDate date, BigDecimal amount, int treasuryId) throws DaoException {
        return executeUpdate(TreasuryStatements.UPDATE_WALLET_FEE, Date.valueOf(date), amount, treasuryId,
                expenseId);
    }

    @Override
    public int delete(int expenseId) throws DaoException {
        return executeUpdate(TreasuryStatements.DELETE_WALLET_FEE, expenseId);
    }

    @Override public List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(ResultSet rs) { throw new UnsupportedOperationException(); }
}
