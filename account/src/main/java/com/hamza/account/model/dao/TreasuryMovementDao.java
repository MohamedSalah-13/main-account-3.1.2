package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Treasury;
import com.hamza.account.model.domain.TreasuryMovement;
import com.hamza.account.type.TreasuryMovementType;
import com.hamza.account.type.TreasuryReferenceType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

public class TreasuryMovementDao extends AbstractDao<TreasuryMovement> {

    private final TreasuryDao treasuryDao;

    public TreasuryMovementDao(Connection connection) {
        super(connection);
        this.treasuryDao = new TreasuryDao(connection);
    }

    @Override
    public TreasuryMovement map(ResultSet rs) throws DaoException {
        try {
            TreasuryMovement movement = new TreasuryMovement();

            movement.setId(rs.getLong("id"));

            Treasury treasury = new Treasury();
            treasury.setId(rs.getInt("treasury_id"));
            treasury.setName(rs.getString("t_name"));
            treasury.setAmount(rs.getBigDecimal("treasury_amount"));
            movement.setTreasury(treasury);

            var movementDate = rs.getDate("movement_date");
            if (movementDate != null) {
                movement.setMovementDate(movementDate.toLocalDate());
            }

            movement.setMovementType(TreasuryMovementType.valueOf(rs.getString("movement_type")));
            movement.setAmountIn(rs.getBigDecimal("amount_in"));
            movement.setAmountOut(rs.getBigDecimal("amount_out"));
            movement.setBalanceAfter(rs.getBigDecimal("balance_after"));

            String referenceType = rs.getString("reference_type");
            if (referenceType != null) {
                movement.setReferenceType(TreasuryReferenceType.valueOf(referenceType));
            }

            long referenceId = rs.getLong("reference_id");
            if (!rs.wasNull()) {
                movement.setReferenceId(referenceId);
            }

            movement.setNotes(rs.getString("notes"));
            movement.setUserId(rs.getInt("user_id"));

            return movement;
        } catch (Exception e) {
            throw new DaoException(e);
        }
    }

    @Override
    public List<TreasuryMovement> loadAll() throws DaoException {
        String query = """
                SELECT tm.id,
                       tm.treasury_id,
                       tm.movement_date,
                       tm.movement_type,
                       tm.amount_in,
                       tm.amount_out,
                       tm.balance_after,
                       tm.reference_type,
                       tm.reference_id,
                       tm.notes,
                       tm.user_id,
                       t.t_name,
                       t.amount AS treasury_amount
                FROM treasury_movements tm
                JOIN treasury t ON t.id = tm.treasury_id
                ORDER BY tm.movement_date DESC, tm.id DESC
                """;

        return queryForObjects(query, this::map);
    }

    public List<TreasuryMovement> loadByTreasuryId(int treasuryId) throws DaoException {
        String query = """
                SELECT tm.id,
                       tm.treasury_id,
                       tm.movement_date,
                       tm.movement_type,
                       tm.amount_in,
                       tm.amount_out,
                       tm.balance_after,
                       tm.reference_type,
                       tm.reference_id,
                       tm.notes,
                       tm.user_id,
                       t.t_name,
                       t.amount AS treasury_amount
                FROM treasury_movements tm
                JOIN treasury t ON t.id = tm.treasury_id
                WHERE tm.treasury_id = ?
                ORDER BY tm.movement_date DESC, tm.id DESC
                """;

        return queryForObjects(query, this::map, treasuryId);
    }

    public List<TreasuryMovement> loadByTreasuryAndDate(
            int treasuryId,
            LocalDate startDate,
            LocalDate endDate
    ) throws DaoException {
        String query = """
                SELECT tm.id,
                       tm.treasury_id,
                       tm.movement_date,
                       tm.movement_type,
                       tm.amount_in,
                       tm.amount_out,
                       tm.balance_after,
                       tm.reference_type,
                       tm.reference_id,
                       tm.notes,
                       tm.user_id,
                       t.t_name,
                       t.amount AS treasury_amount
                FROM treasury_movements tm
                JOIN treasury t ON t.id = tm.treasury_id
                WHERE tm.treasury_id = ?
                  AND tm.movement_date BETWEEN ? AND ?
                ORDER BY tm.movement_date ASC, tm.id ASC
                """;

        return queryForObjects(query, this::map, treasuryId, startDate, endDate);
    }

    public int addInMovement(
            int treasuryId,
            LocalDate movementDate,
            TreasuryMovementType movementType,
            BigDecimal amount,
            TreasuryReferenceType referenceType,
            Long referenceId,
            String notes,
            int userId
    ) throws DaoException {
        validateAmount(amount);

        BigDecimal currentBalance = treasuryDao.getCurrentAmount(treasuryId);
        BigDecimal balanceAfter = currentBalance.add(amount);

        treasuryDao.increaseAmount(treasuryId, amount);

        return insertMovement(
                treasuryId,
                movementDate,
                movementType,
                amount,
                BigDecimal.ZERO,
                balanceAfter,
                referenceType,
                referenceId,
                notes,
                userId
        );
    }

    public int addOutMovement(
            int treasuryId,
            LocalDate movementDate,
            TreasuryMovementType movementType,
            BigDecimal amount,
            TreasuryReferenceType referenceType,
            Long referenceId,
            String notes,
            int userId
    ) throws DaoException {
        validateAmount(amount);

        BigDecimal currentBalance = treasuryDao.getCurrentAmount(treasuryId);

        if (currentBalance.compareTo(amount) < 0) {
            throw new DaoException("رصيد الخزينة غير كافٍ");
        }

        BigDecimal balanceAfter = currentBalance.subtract(amount);

        int affectedRows = treasuryDao.decreaseAmount(treasuryId, amount);

        if (affectedRows == 0) {
            throw new DaoException("رصيد الخزينة غير كافٍ");
        }

        return insertMovement(
                treasuryId,
                movementDate,
                movementType,
                BigDecimal.ZERO,
                amount,
                balanceAfter,
                referenceType,
                referenceId,
                notes,
                userId
        );
    }

    private int insertMovement(
            int treasuryId,
            LocalDate movementDate,
            TreasuryMovementType movementType,
            BigDecimal amountIn,
            BigDecimal amountOut,
            BigDecimal balanceAfter,
            TreasuryReferenceType referenceType,
            Long referenceId,
            String notes,
            int userId
    ) throws DaoException {
        String query = """
                INSERT INTO treasury_movements
                    (treasury_id,
                     movement_date,
                     movement_type,
                     amount_in,
                     amount_out,
                     balance_after,
                     reference_type,
                     reference_id,
                     notes,
                     user_id)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        return executeUpdate(
                query,
                treasuryId,
                movementDate,
                movementType.name(),
                amountIn,
                amountOut,
                balanceAfter,
                referenceType == null ? null : referenceType.name(),
                referenceId,
                notes,
                userId
        );
    }

    private void validateAmount(BigDecimal amount) throws DaoException {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DaoException("يجب إدخال مبلغ أكبر من صفر");
        }
    }
}