package com.hamza.account.model.dao;

import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateTarget;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Log4j2
public class DelegateTargetDao extends AbstractDao<DelegateTarget> {

    private static final String TABLE_NAME = "delegate_targets";

    DelegateTargetDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<DelegateTarget> loadAll() throws DaoException {
        String sql = """
                SELECT dt.*, e.column_name AS delegate_name
                FROM delegate_targets dt
                         JOIN employees e ON e.id = dt.delegate_id
                ORDER BY dt.id DESC
                """;
        return queryForObjects(sql, this::map);
    }

    public List<DelegateTarget> loadByDelegateId(int delegateId) throws DaoException {
        String sql = """
                SELECT dt.*, e.column_name AS delegate_name
                FROM delegate_targets dt
                         JOIN employees e ON e.id = dt.delegate_id
                WHERE dt.delegate_id = ?
                ORDER BY dt.period_from DESC, dt.id DESC
                """;
        return queryForObjects(sql, this::map, delegateId);
    }

    public List<DelegateTarget> loadActiveTargets() throws DaoException {
        String sql = """
                SELECT dt.*, e.column_name AS delegate_name
                FROM delegate_targets dt
                         JOIN employees e ON e.id = dt.delegate_id
                WHERE dt.status = 'ACTIVE'
                ORDER BY dt.period_from DESC, dt.id DESC
                """;
        return queryForObjects(sql, this::map);
    }

    @Override
    public int insert(DelegateTarget target) throws DaoException {
        String sql = """
                INSERT INTO delegate_targets
                (
                    delegate_id,
                    target_name,
                    target_type,
                    period_type,
                    period_from,
                    period_to,
                    target_amount,
                    target_quantity,
                    target_count,
                    min_profit_percent,
                    status,
                    notes,
                    user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    target.getDelegateId(),
                    target.getTargetName(),
                    target.getTargetType(),
                    target.getPeriodType(),
                    target.getPeriodFrom(),
                    target.getPeriodTo(),
                    target.getTargetAmount(),
                    target.getTargetQuantity(),
                    target.getTargetCount(),
                    target.getMinProfitPercent(),
                    target.getStatus(),
                    target.getNotes(),
                    target.getUserId() == 0 ? 1 : target.getUserId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(DelegateTarget target) throws DaoException {
        String sql = """
                UPDATE delegate_targets SET
                    delegate_id = ?,
                    target_name = ?,
                    target_type = ?,
                    period_type = ?,
                    period_from = ?,
                    period_to = ?,
                    target_amount = ?,
                    target_quantity = ?,
                    target_count = ?,
                    min_profit_percent = ?,
                    status = ?,
                    notes = ?,
                    user_id = ?
                WHERE id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    target.getDelegateId(),
                    target.getTargetName(),
                    target.getTargetType(),
                    target.getPeriodType(),
                    target.getPeriodFrom(),
                    target.getPeriodTo(),
                    target.getTargetAmount(),
                    target.getTargetQuantity(),
                    target.getTargetCount(),
                    target.getMinProfitPercent(),
                    target.getStatus(),
                    target.getNotes(),
                    target.getUserId() == 0 ? 1 : target.getUserId(),
                    target.getId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int deleteById(int id) throws DaoException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
        try {
            return executeUpdateWithException(sql, id);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public DelegateTarget getDataById(int id) throws DaoException {
        String sql = """
                SELECT dt.*, e.column_name AS delegate_name
                FROM delegate_targets dt
                         JOIN employees e ON e.id = dt.delegate_id
                WHERE dt.id = ?
                """;
        return queryForObject(sql, this::map, id);
    }

    @Override
    public DelegateTarget getDataByString(String name) throws DaoException {
        String sql = """
                SELECT dt.*, e.column_name AS delegate_name
                FROM delegate_targets dt
                         JOIN employees e ON e.id = dt.delegate_id
                WHERE dt.target_name = ?
                LIMIT 1
                """;
        return queryForObject(sql, this::map, name);
    }

    @Override
    public DelegateTarget map(ResultSet rs) throws DaoException {
        try {
            DelegateTarget target = new DelegateTarget();

            target.setId(rs.getInt("id"));
            target.setDelegateId(rs.getInt("delegate_id"));
            target.setDelegateName(rs.getString("delegate_name"));
            target.setTargetName(rs.getString("target_name"));
            target.setTargetType(rs.getString("target_type"));
            target.setPeriodType(rs.getString("period_type"));

            if (rs.getDate("period_from") != null) {
                target.setPeriodFrom(rs.getDate("period_from").toLocalDate());
            }

            if (rs.getDate("period_to") != null) {
                target.setPeriodTo(rs.getDate("period_to").toLocalDate());
            }

            target.setTargetAmount(rs.getDouble("target_amount"));
            target.setTargetQuantity(rs.getDouble("target_quantity"));
            target.setTargetCount(rs.getInt("target_count"));
            target.setMinProfitPercent(rs.getDouble("min_profit_percent"));
            target.setStatus(rs.getString("status"));
            target.setNotes(rs.getString("notes"));
            target.setUserId(rs.getInt("user_id"));

            return target;
        } catch (SQLException e) {
            log.error("Error mapping DelegateTarget: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    @Override
    public Object[] getData(DelegateTarget target) {
        return new Object[]{
                target.getId(),
                target.getDelegateName(),
                target.getTargetName(),
                target.getTargetType(),
                target.getPeriodFrom(),
                target.getPeriodTo(),
                target.getTargetAmount(),
                target.getTargetQuantity(),
                target.getTargetCount(),
                target.getStatus()
        };
    }
}
