package com.hamza.account.features.shift;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Reads the stored value that must be subtracted before an edit or deletion. */
public final class JdbcShiftCashEffectReader extends AbstractDao<Object> {

    public ShiftCashEffect document(DocumentType type, int id) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        ShiftCashSource source = ShiftCashSource.document(type);
        String sql = "SELECT treasury_id, shift_id, " + spec.paid() + " AS cash_amount FROM "
                + spec.table() + " WHERE " + spec.key() + "=? FOR UPDATE";
        return one(sql, id, rs -> type.cashSign() > 0
                ? ShiftCashEffect.incoming(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                    rs.getBigDecimal("cash_amount"))
                : ShiftCashEffect.outgoing(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                    rs.getBigDecimal("cash_amount")));
    }

    public ShiftCashEffect party(PartyKind kind, int id) throws DaoException {
        PartyLedgerSpec spec = PartyLedgerSpec.of(kind);
        ShiftCashSource source = ShiftCashSource.party(kind);
        String sql = "SELECT treasury_id, shift_id, paid AS cash_amount FROM " + spec.table()
                + " WHERE " + PartyLedgerSpec.KEY + "=? FOR UPDATE";
        return one(sql, id, rs -> kind == PartyKind.CUSTOMER
                ? ShiftCashEffect.incoming(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                    rs.getBigDecimal("cash_amount"))
                : ShiftCashEffect.outgoing(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                    rs.getBigDecimal("cash_amount")));
    }

    public ShiftCashEffect expense(int id) throws DaoException {
        return one("SELECT treasury_id, shift_id, amount AS cash_amount FROM expenses_details WHERE id=? FOR UPDATE",
                id, rs -> ShiftCashEffect.outgoing(ShiftCashSource.EXPENSE, id,
                        rs.getInt("treasury_id"), nullableInt(rs, "shift_id"), rs.getBigDecimal("cash_amount")));
    }

    public ShiftCashEffect cash(int id) throws DaoException {
        return one("SELECT treasury_id, shift_id, amount AS cash_amount, deposit_or_expenses "
                        + "FROM treasury_deposit_expenses WHERE id=? FOR UPDATE", id, rs -> {
            CashDirection direction = CashDirection.fromCode(rs.getInt("deposit_or_expenses"));
            ShiftCashSource source = direction == CashDirection.DEPOSIT
                    ? ShiftCashSource.CASH_DEPOSIT : ShiftCashSource.CASH_WITHDRAWAL;
            return direction == CashDirection.DEPOSIT
                    ? ShiftCashEffect.incoming(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                        rs.getBigDecimal("cash_amount"))
                    : ShiftCashEffect.outgoing(source, id, rs.getInt("treasury_id"), nullableInt(rs, "shift_id"),
                        rs.getBigDecimal("cash_amount"));
        });
    }

    public List<ShiftCashEffect> transfer(int id) throws DaoException {
        String sql = "SELECT treasury_from, treasury_to, source_shift_id, destination_shift_id, amount "
                + "FROM treasury_transfers WHERE id=? FOR UPDATE";
        return withConnection(connection -> {
            try (var ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return List.of();
                    BigDecimal amount = rs.getBigDecimal("amount");
                    return List.of(
                            ShiftCashEffect.outgoing(ShiftCashSource.TRANSFER_OUT, id,
                                    rs.getInt("treasury_from"), nullableInt(rs, "source_shift_id"), amount),
                            ShiftCashEffect.incoming(ShiftCashSource.TRANSFER_IN, id,
                                    rs.getInt("treasury_to"), nullableInt(rs, "destination_shift_id"), amount));
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read transfer cash effect", e);
            }
        });
    }

    private ShiftCashEffect one(String sql, int id, Row row) throws DaoException {
        return withConnection(connection -> {
            try (var ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? row.map(rs) : null;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read shift cash effect", e);
            }
        });
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @FunctionalInterface
    private interface Row { ShiftCashEffect map(ResultSet rs) throws SQLException; }

    @Override public List<Object> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(Object value) { throw new UnsupportedOperationException(); }
    @Override public int update(Object value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(Object value) { throw new UnsupportedOperationException(); }
    @Override public Object map(ResultSet rs) { throw new UnsupportedOperationException(); }
}
