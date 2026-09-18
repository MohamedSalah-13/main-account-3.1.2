package com.hamza.account.model.dao;

import com.hamza.account.features.treasury.CashCategory;
import com.hamza.account.features.treasury.CashDirection;
import com.hamza.account.features.treasury.CashMovement;
import com.hamza.account.features.treasury.CashMovementCommand;
import com.hamza.account.features.treasury.TreasuryHistoryFilter;
import com.hamza.account.features.treasury.TreasuryHistoryPage;
import com.hamza.account.treasury.TreasuryStatements;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes and reads {@code treasury_deposit_expenses} - the hand-entered deposits and
 * withdrawals.
 * <p>
 * Like {@code treasury_transfers}, the table has been read for years and never
 * written: {@code treasury_balance} sums it, and {@code UserShiftDao} reports "total
 * deposits" for a shift, over rows the application had no way to create.
 */
public class CashMovementDao extends AbstractDao<CashMovement> {

    public int insert(CashMovementCommand command) throws DaoException {
        return insert(command, null);
    }

    public int insert(CashMovementCommand command, Integer shiftId) throws DaoException {
        insertReturningId(command, shiftId);
        return 1;
    }

    public int insertReturningId(CashMovementCommand command, Integer shiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    TreasuryStatements.INSERT_CASH_MOVEMENT_WITH_SHIFT, Statement.RETURN_GENERATED_KEYS)) {
                Object[] data = {command.statement(), Date.valueOf(command.date()), command.amount(),
                        command.description(), command.direction().code(), command.category().code(),
                        command.treasuryId(), command.userId(), shiftId};
                for (int i = 0; i < data.length; i++) statement.setObject(i + 1, data[i]);
                if (statement.executeUpdate() != 1) throw new DaoException("Cash movement was not inserted");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
                throw new DaoException("Cash movement id was not generated");
            } catch (SQLException e) {
                throw new DaoException("Could not insert cash movement", e);
            }
        });
    }

    public List<CashMovement> recent(int limit) throws DaoException {
        return queryForObjects(TreasuryStatements.SELECT_RECENT_CASH_MOVEMENTS, this::map, limit);
    }

    /** The owner's own movements in a period, for the capital report. */
    public List<CashMovement> capitalBetween(LocalDate from, LocalDate to) throws DaoException {
        return queryForObjects(TreasuryStatements.SELECT_CAPITAL_MOVEMENTS, this::map,
                Date.valueOf(from), Date.valueOf(to));
    }

    /** One page and one extra row, in the filter's order. */
    public List<CashMovement> page(TreasuryHistoryFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_CASH_MOVEMENTS_PAGE)) {
                Object[] where = whereValues(filter);
                Object[] values = java.util.Arrays.copyOf(where, where.length + 2);
                values[where.length] = filter.queryLimit();
                values[where.length + 1] = filter.offset();
                setData(statement, values);
                List<CashMovement> rows = new java.util.ArrayList<>();
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
            try (var statement = connection.prepareStatement(TreasuryStatements.SELECT_CASH_MOVEMENTS_TOTALS)) {
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
        Integer direction = filter.direction() == null ? null : filter.direction().code();
        return new Object[]{Date.valueOf(filter.from()), Date.valueOf(filter.to()),
                filter.treasuryId(), filter.treasuryId(), direction, direction};
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(TreasuryStatements.DELETE_CASH_MOVEMENT, id);
    }

    @Override
    public CashMovement map(ResultSet rs) throws DaoException {
        try {
            return new CashMovement(
                    rs.getInt("id"),
                    rs.getInt("treasury_id"),
                    rs.getString("t_name"),
                    CashDirection.fromCode(rs.getInt("deposit_or_expenses")),
                    CashCategory.fromCode(rs.getString("category")),
                    rs.getBigDecimal("amount"),
                    rs.getDate("date_inter").toLocalDate(),
                    rs.getString("statement"),
                    rs.getString("description_data"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
