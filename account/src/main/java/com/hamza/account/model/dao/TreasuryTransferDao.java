package com.hamza.account.model.dao;

import com.hamza.account.features.treasury.TreasuryTransfer;
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

    public int insertReturningId(TreasuryTransferCommand command, Integer sourceShiftId,
                                 Integer destinationShiftId) throws DaoException {
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    TreasuryStatements.INSERT_TRANSFER_WITH_SHIFTS, Statement.RETURN_GENERATED_KEYS)) {
                Object[] data = {command.fromTreasuryId(), command.toTreasuryId(), command.amount(),
                        Date.valueOf(command.transferDate()), command.notes(), command.userId(),
                        sourceShiftId, destinationShiftId};
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
                    rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
