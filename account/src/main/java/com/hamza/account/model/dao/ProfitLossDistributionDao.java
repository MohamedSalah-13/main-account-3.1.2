package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ProfitLossDistribution;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

@Log4j2
public class ProfitLossDistributionDao extends AbstractDao<ProfitLossDistribution> {

    private final String TABLE_NAME = "profit_loss_distribution";
    private final String VIEW_NAME = "v_profit_loss_distribution_report";
    private final String ID = "id";
    private final String CAPITAL_ID = "capital_id";
    private final String DISTRIBUTION_DATE = "distribution_date";
    private final String PERIOD_FROM = "period_from";
    private final String PERIOD_TO = "period_to";
    private final String TOTAL_REVENUE = "total_revenue";
    private final String TOTAL_EXPENSES = "total_expenses";
    private final String NET_PROFIT_LOSS = "net_profit_loss";
    private final String IS_PROFIT = "is_profit";
    private final String DISTRIBUTION_STATUS = "distribution_status";
    private final String NOTES = "notes";
    private final String USER_ID = "user_id";

    ProfitLossDistributionDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<ProfitLossDistribution> loadAll() throws DaoException {
        return queryForObjects("SELECT * FROM " + VIEW_NAME, this::map);
    }

    public List<ProfitLossDistribution> getDistributionsByCapitalId(int capitalId) throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE capital_id = ? ORDER BY distribution_date DESC";
        return queryForObjects(sql, this::map, capitalId);
    }

    public List<ProfitLossDistribution> getPendingDistributions() throws DaoException {
        String sql = "SELECT * FROM " + VIEW_NAME + " WHERE distribution_status = 'PENDING'";
        return queryForObjects(sql, this::map);
    }

    @Override
    public int insert(ProfitLossDistribution distribution) throws DaoException {
        String sql = """
                INSERT INTO profit_loss_distribution (
                    capital_id, distribution_date, period_from, period_to,
                    total_revenue, total_expenses, net_profit_loss, is_profit,
                    distribution_status, notes, user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    distribution.getCapitalId(),
                    distribution.getDistributionDate(),
                    distribution.getPeriodFrom(),
                    distribution.getPeriodTo(),
                    distribution.getTotalRevenue(),
                    distribution.getTotalExpenses(),
                    distribution.getNetProfitLoss(),
                    distribution.isIsProfit() ? 1 : 0,
                    distribution.getDistributionStatus(),
                    distribution.getNotes(),
                    distribution.getUserId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int update(ProfitLossDistribution distribution) throws DaoException {
        String sql = """
                UPDATE profit_loss_distribution SET
                    distribution_date = ?,
                    period_from = ?,
                    period_to = ?,
                    total_revenue = ?,
                    total_expenses = ?,
                    net_profit_loss = ?,
                    is_profit = ?,
                    distribution_status = ?,
                    notes = ?
                WHERE id = ?
                """;

        try {
            return executeUpdateWithException(
                    sql,
                    distribution.getDistributionDate(),
                    distribution.getPeriodFrom(),
                    distribution.getPeriodTo(),
                    distribution.getTotalRevenue(),
                    distribution.getTotalExpenses(),
                    distribution.getNetProfitLoss(),
                    distribution.isIsProfit() ? 1 : 0,
                    distribution.getDistributionStatus(),
                    distribution.getNotes(),
                    distribution.getId()
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public int deleteById(int id) throws DaoException {
        try {
            return executeUpdateWithException(
                    SqlStatements.deleteStatement(TABLE_NAME, ID),
                    id
            );
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public ProfitLossDistribution getDataById(int id) throws DaoException {
        return queryForObject(
                "SELECT * FROM " + VIEW_NAME + " WHERE id = ?",
                this::map,
                id
        );
    }

    /**
     * توزيع الأرباح/الخسائر باستخدام Stored Procedure
     */
    public void distributeProfit(int distributionId, int userId) throws DaoException {
        String sql = "CALL sp_distribute_profit_loss(?, ?)";
        try {
            executeUpdateWithException(sql, distributionId, userId);
            log.info("تم توزيع الأرباح/الخسائر بنجاح - Distribution ID: {}", distributionId);
        } catch (SQLException e) {
            log.error("خطأ في توزيع الأرباح/الخسائر", e);
            throw new DaoException(e);
        }
    }

    /**
     * حساب الأرباح/الخسائر لفترة محددة
     */
    public ProfitLossDistribution calculateProfitLoss(int capitalId, LocalDate periodFrom, LocalDate periodTo) throws DaoException {
        String sql = """
                SELECT 
                    ? AS capital_id,
                    CURDATE() AS distribution_date,
                    ? AS period_from,
                    ? AS period_to,
                    COALESCE(SUM(CASE WHEN ts.invoice_date BETWEEN ? AND ? THEN ts.total - ts.discount ELSE 0 END), 0) AS total_revenue,
                    COALESCE((SELECT SUM(amount) FROM expenses_details WHERE date BETWEEN ? AND ?), 0) AS total_expenses,
                    0 AS net_profit_loss,
                    1 AS is_profit,
                    'PENDING' AS distribution_status
                FROM total_sales ts
                WHERE ts.invoice_date BETWEEN ? AND ?
                """;

        return queryForObject(sql, this::mapCalculated, capitalId, periodFrom, periodTo, 
                periodFrom, periodTo, periodFrom, periodTo, periodFrom, periodTo);
    }

    @Override
    public ProfitLossDistribution map(ResultSet rs) throws DaoException {
        try {
            ProfitLossDistribution distribution = new ProfitLossDistribution();
            distribution.setId(rs.getInt(ID));
            distribution.setCapitalId(rs.getInt(CAPITAL_ID));
            
            // From view
            distribution.setCapitalName(rs.getString("capital_name"));
            
            if (rs.getDate(DISTRIBUTION_DATE) != null) {
                distribution.setDistributionDate(rs.getDate(DISTRIBUTION_DATE).toLocalDate());
            }
            if (rs.getDate(PERIOD_FROM) != null) {
                distribution.setPeriodFrom(rs.getDate(PERIOD_FROM).toLocalDate());
            }
            if (rs.getDate(PERIOD_TO) != null) {
                distribution.setPeriodTo(rs.getDate(PERIOD_TO).toLocalDate());
            }
            
            distribution.setTotalRevenue(rs.getDouble(TOTAL_REVENUE));
            distribution.setTotalExpenses(rs.getDouble(TOTAL_EXPENSES));
            distribution.setNetProfitLoss(rs.getDouble(NET_PROFIT_LOSS));
            distribution.setIsProfit(rs.getInt(IS_PROFIT) == 1);
            distribution.setDistributionStatus(rs.getString(DISTRIBUTION_STATUS));
            
            if (rs.getTimestamp("distributed_at") != null) {
                distribution.setDistributedAt(rs.getTimestamp("distributed_at").toLocalDateTime());
            }
            
            distribution.setNotes(rs.getString(NOTES));
            
            return distribution;
        } catch (SQLException e) {
            log.error("Error mapping ProfitLossDistribution: {}", e.getMessage());
            throw new DaoException(e.getMessage());
        }
    }

    private ProfitLossDistribution mapCalculated(ResultSet rs) throws DaoException {
        try {
            ProfitLossDistribution distribution = new ProfitLossDistribution();
            distribution.setCapitalId(rs.getInt("capital_id"));
            distribution.setDistributionDate(rs.getDate("distribution_date").toLocalDate());
            distribution.setPeriodFrom(rs.getDate("period_from").toLocalDate());
            distribution.setPeriodTo(rs.getDate("period_to").toLocalDate());
            distribution.setTotalRevenue(rs.getDouble("total_revenue"));
            distribution.setTotalExpenses(rs.getDouble("total_expenses"));
            
            double net = distribution.getTotalRevenue() - distribution.getTotalExpenses();
            distribution.setNetProfitLoss(Math.abs(net));
            distribution.setIsProfit(net >= 0);
            distribution.setDistributionStatus("PENDING");
            
            return distribution;
        } catch (SQLException e) {
            log.error("Error mapping calculated ProfitLossDistribution: {}", e.getMessage());
            throw new DaoException(e.getMessage());
        }
    }

    @Override
    public Object[] getData(ProfitLossDistribution distribution) throws DaoException {
        return new Object[]{
                distribution.getId(),
                distribution.getCapitalName(),
                distribution.getDistributionDate(),
                distribution.getPeriodFrom(),
                distribution.getPeriodTo(),
                distribution.getTotalRevenue(),
                distribution.getTotalExpenses(),
                distribution.getNetProfitLoss(),
                distribution.isIsProfit() ? "ربح" : "خسارة",
                distribution.getDistributionStatus()
        };
    }
}
