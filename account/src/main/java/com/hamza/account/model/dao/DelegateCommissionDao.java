package com.hamza.account.model.dao;

import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegateCommission;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class DelegateCommissionDao extends AbstractDao<DelegateCommission> {

    private static final String TABLE_NAME = "delegate_commissions";

    DelegateCommissionDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<DelegateCommission> loadAll() throws DaoException {
        String sql = """
                SELECT dc.*,
                       e.column_name AS delegate_name,
                       t.t_name AS treasury_name
                FROM delegate_commissions dc
                         JOIN employees e ON e.id = dc.delegate_id
                         LEFT JOIN treasury t ON t.id = dc.treasury_id
                ORDER BY dc.id DESC
                """;
        return queryForObjects(sql, this::map);
    }

    public List<DelegateCommission> loadByDelegateId(int delegateId) throws DaoException {
        String sql = """
                SELECT dc.*,
                       e.column_name AS delegate_name,
                       t.t_name AS treasury_name
                FROM delegate_commissions dc
                         JOIN employees e ON e.id = dc.delegate_id
                         LEFT JOIN treasury t ON t.id = dc.treasury_id
                WHERE dc.delegate_id = ?
                ORDER BY dc.commission_date DESC, dc.id DESC
                """;
        return queryForObjects(sql, this::map, delegateId);
    }

    public List<DelegateCommission> loadByPeriod(Integer delegateId, LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        String sql = """
                SELECT dc.*,
                       e.column_name AS delegate_name,
                       t.t_name AS treasury_name
                FROM delegate_commissions dc
                         JOIN employees e ON e.id = dc.delegate_id
                         LEFT JOIN treasury t ON t.id = dc.treasury_id
                WHERE dc.commission_date BETWEEN ? AND ?
                  AND (? IS NULL OR dc.delegate_id = ?)
                ORDER BY dc.commission_date DESC, dc.id DESC
                """;
        return queryForObjects(sql, this::map, dateFrom, dateTo, delegateId, delegateId);
    }

    @Override
    public int insert(DelegateCommission commission) throws DaoException {
        String sql = """
                INSERT INTO delegate_commissions
                (
                    delegate_id,
                    commission_date,
                    reference_type,
                    reference_id,
                    sales_amount,
                    profit_amount,
                    commission_type,
                    commission_rate,
                    commission_amount,
                    payment_status,
                    paid_amount,
                    payment_date,
                    treasury_id,
                    notes,
                    user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    commission.getDelegateId(),
                    commission.getCommissionDate(),
                    commission.getReferenceType(),
                    nullableLong(commission.getReferenceId()),
                    commission.getSalesAmount(),
                    commission.getProfitAmount(),
                    commission.getCommissionType(),
                    commission.getCommissionRate(),
                    commission.getCommissionAmount(),
                    commission.getPaymentStatus(),
                    commission.getPaidAmount(),
                    commission.getPaymentDate(),
                    nullableInt(commission.getTreasuryId()),
                    commission.getNotes(),
                    commission.getUserId() == 0 ? 1 : commission.getUserId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(DelegateCommission commission) throws DaoException {
        String sql = """
                UPDATE delegate_commissions SET
                    delegate_id = ?,
                    commission_date = ?,
                    reference_type = ?,
                    reference_id = ?,
                    sales_amount = ?,
                    profit_amount = ?,
                    commission_type = ?,
                    commission_rate = ?,
                    commission_amount = ?,
                    payment_status = ?,
                    paid_amount = ?,
                    payment_date = ?,
                    treasury_id = ?,
                    notes = ?,
                    user_id = ?
                WHERE id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    commission.getDelegateId(),
                    commission.getCommissionDate(),
                    commission.getReferenceType(),
                    nullableLong(commission.getReferenceId()),
                    commission.getSalesAmount(),
                    commission.getProfitAmount(),
                    commission.getCommissionType(),
                    commission.getCommissionRate(),
                    commission.getCommissionAmount(),
                    commission.getPaymentStatus(),
                    commission.getPaidAmount(),
                    commission.getPaymentDate(),
                    nullableInt(commission.getTreasuryId()),
                    commission.getNotes(),
                    commission.getUserId() == 0 ? 1 : commission.getUserId(),
                    commission.getId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public int payCommission(long commissionId, double paidAmount, int treasuryId, LocalDate paymentDate) throws DaoException {
        String sql = """
                UPDATE delegate_commissions SET
                    paid_amount = paid_amount + ?,
                    payment_date = ?,
                    treasury_id = ?,
                    payment_status = CASE
                        WHEN paid_amount + ? >= commission_amount THEN 'PAID'
                        WHEN paid_amount + ? > 0 THEN 'PARTIAL'
                        ELSE 'UNPAID'
                    END
                WHERE id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    paidAmount,
                    paymentDate,
                    treasuryId,
                    paidAmount,
                    paidAmount,
                    commissionId
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public DelegateCommission calculateCommission(int delegateId, LocalDate dateFrom, LocalDate dateTo, int userId) throws DaoException {
        String sql = "{CALL sp_calculate_delegate_commission(?, ?, ?, ?)}";

        try (CallableStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, delegateId);
            statement.setDate(2, Date.valueOf(dateFrom));
            statement.setDate(3, Date.valueOf(dateTo));
            statement.setInt(4, userId <= 0 ? 1 : userId);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    DelegateCommission commission = new DelegateCommission();

                    commission.setId(rs.getLong("commission_id"));
                    commission.setDelegateId(rs.getInt("delegate_id"));
                    commission.setCommissionType(rs.getString("commission_type"));
                    commission.setCommissionRate(rs.getDouble("commission_value"));
                    commission.setSalesAmount(rs.getDouble("sales_amount"));
                    commission.setProfitAmount(rs.getDouble("profit_amount"));
                    commission.setCommissionAmount(rs.getDouble("commission_amount"));
                    commission.setCommissionDate(LocalDate.now());
                    commission.setReferenceType("PERIOD");
                    commission.setPaymentStatus("UNPAID");

                    return commission;
                }
            }

            return null;
        } catch (SQLException e) {
            log.error("Error calculating delegate commission: {}", e.getMessage());
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

    public int deleteById(long id) throws DaoException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";
        try {
            return executeUpdateWithException(sql, id);
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public DelegateCommission getDataById(int id) throws DaoException {
        return getDataById((long) id);
    }

    public DelegateCommission getDataById(long id) throws DaoException {
        String sql = """
                SELECT dc.*,
                       e.column_name AS delegate_name,
                       t.t_name AS treasury_name
                FROM delegate_commissions dc
                         JOIN employees e ON e.id = dc.delegate_id
                         LEFT JOIN treasury t ON t.id = dc.treasury_id
                WHERE dc.id = ?
                """;
        return queryForObject(sql, this::map, id);
    }

    @Override
    public DelegateCommission getDataByString(String name) throws DaoException {
        String sql = """
                SELECT dc.*,
                       e.column_name AS delegate_name,
                       t.t_name AS treasury_name
                FROM delegate_commissions dc
                         JOIN employees e ON e.id = dc.delegate_id
                         LEFT JOIN treasury t ON t.id = dc.treasury_id
                WHERE e.column_name = ?
                ORDER BY dc.id DESC
                LIMIT 1
                """;
        return queryForObject(sql, this::map, name);
    }

    @Override
    public DelegateCommission map(ResultSet rs) throws DaoException {
        try {
            DelegateCommission commission = new DelegateCommission();

            commission.setId(rs.getLong("id"));
            commission.setDelegateId(rs.getInt("delegate_id"));
            commission.setDelegateName(rs.getString("delegate_name"));

            if (rs.getDate("commission_date") != null) {
                commission.setCommissionDate(rs.getDate("commission_date").toLocalDate());
            }

            commission.setReferenceType(rs.getString("reference_type"));

            commission.setReferenceId(rs.getLong("reference_id"));
            if (rs.wasNull()) {
                commission.setReferenceId(0);
            }

            commission.setSalesAmount(rs.getDouble("sales_amount"));
            commission.setProfitAmount(rs.getDouble("profit_amount"));
            commission.setCommissionType(rs.getString("commission_type"));
            commission.setCommissionRate(rs.getDouble("commission_rate"));
            commission.setCommissionAmount(rs.getDouble("commission_amount"));
            commission.setPaymentStatus(rs.getString("payment_status"));
            commission.setPaidAmount(rs.getDouble("paid_amount"));

            if (rs.getDate("payment_date") != null) {
                commission.setPaymentDate(rs.getDate("payment_date").toLocalDate());
            }

            commission.setTreasuryId(rs.getInt("treasury_id"));
            if (rs.wasNull()) {
                commission.setTreasuryId(0);
            }

            commission.setTreasuryName(rs.getString("treasury_name"));
            commission.setNotes(rs.getString("notes"));
            commission.setUserId(rs.getInt("user_id"));

            return commission;
        } catch (SQLException e) {
            log.error("Error mapping DelegateCommission: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    @Override
    public Object[] getData(DelegateCommission commission) {
        return new Object[]{
                commission.getId(),
                commission.getDelegateName(),
                commission.getCommissionDate(),
                commission.getSalesAmount(),
                commission.getProfitAmount(),
                commission.getCommissionType(),
                commission.getCommissionRate(),
                commission.getCommissionAmount(),
                commission.getPaidAmount(),
                commission.getPaymentStatus()
        };
    }

    private Object nullableInt(int value) {
        return value <= 0 ? null : value;
    }

    private Object nullableLong(long value) {
        return value <= 0 ? null : value;
    }
}
