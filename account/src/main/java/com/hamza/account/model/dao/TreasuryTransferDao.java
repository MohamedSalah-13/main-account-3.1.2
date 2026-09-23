package com.hamza.account.model.dao;

import com.hamza.account.features.treasury.TreasuryTransfer;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.features.treasury.TreasuryVoucherLayout;
import com.hamza.account.features.treasury.TreasuryTransferCommand;
import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Writes and reads {@code treasury_transfers} - a table that has existed since the
 * baseline with a view over it, a delete rule protecting it and a period-lock rule
 * declared for it, and <b>no writer in Java at all</b>.
 * <p>
 * Every rule about a transfer - who may make one, whether the period is open,
 * whether the source has the money - lives in {@code TreasuryTransferService}. This
 * class holds the mapping and the parameter order and nothing else, which is why it
 * takes a command rather than reading a screen.
 */
public class TreasuryTransferDao extends AbstractDao<TreasuryTransfer> {

    public int insert(TreasuryTransferCommand command) throws DaoException {
        return executeUpdate(TreasuryStatements.INSERT_TRANSFER,
                command.fromTreasuryId(), command.toTreasuryId(), command.amount(),
                Date.valueOf(command.transferDate()), command.notes(), command.userId());
    }

    public int insert(TreasuryTransferCommand command, Integer sourceShiftId,
                      Integer destinationShiftId) throws DaoException {
        insertReturningId(command, sourceShiftId, destinationShiftId);
        return 1;
    }

    /** A transfer between two treasuries in the base: {@code command.amount()} is the whole of it. */
    public int insertReturningId(TreasuryTransferCommand command, Integer sourceShiftId,
                                 Integer destinationShiftId) throws DaoException {
        return insertReturningId(command, null, null, sourceShiftId, destinationShiftId);
    }

    /**
     * {@code command.amount()} is what the books move, in the base; {@code amountFrom}/{@code amountTo}
     * what each side gave or received in its own currency, {@code null} for a side in the base (V81).
     */
    public int insertReturningId(TreasuryTransferCommand command, java.math.BigDecimal amountFrom,
                                 java.math.BigDecimal amountTo, Integer sourceShiftId,
                                 Integer destinationShiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    TreasuryStatements.INSERT_TRANSFER_WITH_SHIFTS, Statement.RETURN_GENERATED_KEYS)) {
                Object[] data = {command.fromTreasuryId(), command.toTreasuryId(), command.amount(),
                        Date.valueOf(command.transferDate()), command.notes(), command.userId(),
                        sourceShiftId, destinationShiftId, amountFrom, amountTo};
                for (int i = 0; i < data.length; i++) statement.setObject(i + 1, data[i]);
                if (statement.executeUpdate() != 1) throw new DaoException("Transfer was not inserted");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
                throw new DaoException("Transfer id was not generated");
            } catch (SQLException e) {
                throw new DaoException("Could not insert transfer", e);
            }
        });
    }

    public List<TreasuryTransfer> recent(int limit) throws DaoException {
        return queryForObjects(TreasuryStatements.SELECT_RECENT_TRANSFERS, this::map, limit);
    }

    /** One page and one extra row, in the filter's order. */
    public List<TreasuryTransfer> page(TreasuryHistoryFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_TRANSFERS_PAGE)) {
                Object[] where = whereValues(filter);
                Object[] values = java.util.Arrays.copyOf(where, where.length + 2);
                values[where.length] = filter.queryLimit();
                values[where.length + 1] = filter.offset();
                setData(statement, values);
                List<TreasuryTransfer> rows = new java.util.ArrayList<>();
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) rows.add(map(rs));
                }
                return rows;
            } catch (SQLException e) {
                throw new DaoException("Could not read the treasury history", e);
            }
        });
    }

    /** The count and the two sums of the whole filtered set - the same WHERE, the same values. */
    public TreasuryHistoryPage.Totals totals(TreasuryHistoryFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_TRANSFERS_TOTALS)) {
                setData(statement, whereValues(filter));
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    return new TreasuryHistoryPage.Totals(rs.getLong("movements"),
                            rs.getBigDecimal("first_total"), rs.getBigDecimal("second_total"));
                }
            } catch (SQLException e) {
                throw new DaoException("Could not total the treasury history", e);
            }
        });
    }

    /** Every value the shared WHERE binds, in the order it binds them. */
    static Object[] whereValues(TreasuryHistoryFilter filter) {
        return new Object[]{Date.valueOf(filter.from()), Date.valueOf(filter.to()),
                filter.treasuryId(), filter.treasuryId(), filter.treasuryId()};
    }

    /** The stored movement and who entered it, for its voucher; {@code null} when it is gone. */
    public TreasuryVoucherLayout.TransferVoucher voucher(int id) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_TRANSFER_FOR_VOUCHER)) {
                statement.setInt(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? new TreasuryVoucherLayout.TransferVoucher(map(rs), rs.getString("user_name")) : null;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read the movement for its voucher", e);
            }
        });
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(TreasuryStatements.DELETE_TRANSFER, id);
    }

    @Override
    public TreasuryTransfer map(ResultSet rs) throws DaoException {
        try {
            return new TreasuryTransfer(
                    rs.getInt("id"),
                    rs.getInt("treasury_from"),
                    rs.getString("treasury_name_from"),
                    rs.getInt("treasury_to"),
                    rs.getString("treasury_name_to"),
                    rs.getBigDecimal("amount"),
                    rs.getDate("transfer_date").toLocalDate(),
                    rs.getString("notes"),
                    rs.getBigDecimal("fee"),
                    rs.getBigDecimal("amount_from"),
                    rs.getBigDecimal("amount_to"),
                    rs.getString("currency_from"),
                    rs.getString("currency_to"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
