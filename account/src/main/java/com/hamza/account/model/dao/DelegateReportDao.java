package com.hamza.account.model.dao;

import com.hamza.account.database.AbstractDao;
import com.hamza.account.database.DaoException;
import com.hamza.account.model.domain.DelegatePerformanceReport;
import com.hamza.account.model.domain.DelegateTargetReport;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class DelegateReportDao extends AbstractDao<DelegatePerformanceReport> {

    DelegateReportDao(Connection connection) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public List<DelegatePerformanceReport> loadAll() throws DaoException {
        String sql = "SELECT * FROM v_delegate_sales_performance ORDER BY report_date DESC, delegate_name";
        return queryForObjects(sql, this::map);
    }

    public List<DelegatePerformanceReport> performanceReport(Integer delegateId, LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        String sql = """
                SELECT *
                FROM v_delegate_sales_performance
                WHERE report_date BETWEEN ? AND ?
                  AND (? IS NULL OR delegate_id = ?)
                ORDER BY report_date DESC, delegate_name
                """;

        return queryForObjects(
                sql,
                this::map,
                dateFrom,
                dateTo,
                delegateId,
                delegateId
        );
    }

    public List<DelegatePerformanceReport> performanceSummary(Integer delegateId, LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        String sql = "{CALL sp_delegate_performance_report(?, ?, ?)}";
        List<DelegatePerformanceReport> list = new ArrayList<>();

        try (CallableStatement statement = connection.prepareCall(sql)) {
            if (delegateId == null || delegateId <= 0) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, delegateId);
            }

            statement.setDate(2, Date.valueOf(dateFrom));
            statement.setDate(3, Date.valueOf(dateTo));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapSummary(rs));
                }
            }

            return list;
        } catch (SQLException e) {
            log.error("Error loading delegate performance summary: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    public List<DelegateTargetReport> targetReport(Integer delegateId, LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        String sql = "{CALL sp_delegate_targets_report(?, ?, ?)}";
        List<DelegateTargetReport> list = new ArrayList<>();

        try (CallableStatement statement = connection.prepareCall(sql)) {
            if (delegateId == null || delegateId <= 0) {
                statement.setNull(1, Types.INTEGER);
            } else {
                statement.setInt(1, delegateId);
            }

            statement.setDate(2, Date.valueOf(dateFrom));
            statement.setDate(3, Date.valueOf(dateTo));

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapTargetReport(rs));
                }
            }

            return list;
        } catch (SQLException e) {
            log.error("Error loading delegate target report: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    @Override
    public int insert(DelegatePerformanceReport delegatePerformanceReport) throws DaoException {
        throw new DaoException("Report DAO does not support insert");
    }

    @Override
    public int update(DelegatePerformanceReport delegatePerformanceReport) throws DaoException {
        throw new DaoException("Report DAO does not support update");
    }

    @Override
    public int deleteById(int id) throws DaoException {
        throw new DaoException("Report DAO does not support delete");
    }

    @Override
    public DelegatePerformanceReport getDataById(int id) throws DaoException {
        throw new DaoException("Report DAO does not support getDataById");
    }

    @Override
    public DelegatePerformanceReport getDataByString(String name) throws DaoException {
        throw new DaoException("Report DAO does not support getDataByString");
    }

    @Override
    public DelegatePerformanceReport map(ResultSet rs) throws DaoException {
        try {
            DelegatePerformanceReport report = new DelegatePerformanceReport();

            report.setDelegateId(rs.getInt("delegate_id"));
            report.setDelegateName(rs.getString("delegate_name"));

            if (rs.getDate("report_date") != null) {
                report.setReportDate(rs.getDate("report_date").toLocalDate());
            }

            report.setInvoicesCount(rs.getInt("invoices_count"));
            report.setCustomersCount(rs.getInt("customers_count"));
            report.setGrossSales(rs.getDouble("gross_sales"));
            report.setSalesDiscount(rs.getDouble("sales_discount"));
            report.setReturnsCount(rs.getInt("returns_count"));
            report.setGrossReturns(rs.getDouble("gross_returns"));
            report.setReturnsDiscount(rs.getDouble("returns_discount"));
            report.setNetSales(rs.getDouble("net_sales"));
            report.setInvoiceCashCollected(rs.getDouble("invoice_cash_collected"));
            report.setAccountCollections(rs.getDouble("account_collections"));
            report.setTotalCollected(rs.getDouble("total_collected"));
            report.setReturnedCash(rs.getDouble("returned_cash"));
            report.setNetProfit(rs.getDouble("net_profit"));
            report.setProfitPercent(rs.getDouble("profit_percent"));

            return report;
        } catch (SQLException e) {
            log.error("Error mapping DelegatePerformanceReport: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    private DelegatePerformanceReport mapSummary(ResultSet rs) throws DaoException {
        try {
            DelegatePerformanceReport report = new DelegatePerformanceReport();

            report.setDelegateId(rs.getInt("delegate_id"));
            report.setDelegateName(rs.getString("delegate_name"));
            report.setInvoicesCount(rs.getInt("invoices_count"));
            report.setCustomersCount(rs.getInt("customers_count"));
            report.setGrossSales(rs.getDouble("gross_sales"));
            report.setSalesDiscount(rs.getDouble("sales_discount"));
            report.setGrossReturns(rs.getDouble("gross_returns"));
            report.setReturnsDiscount(rs.getDouble("returns_discount"));
            report.setNetSales(rs.getDouble("net_sales"));
            report.setInvoiceCashCollected(rs.getDouble("invoice_cash_collected"));
            report.setAccountCollections(rs.getDouble("account_collections"));
            report.setTotalCollected(rs.getDouble("total_collected"));
            report.setReturnedCash(rs.getDouble("returned_cash"));
            report.setNetProfit(rs.getDouble("net_profit"));
            report.setProfitPercent(rs.getDouble("profit_percent"));

            return report;
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    public DelegateTargetReport mapTargetReport(ResultSet rs) throws DaoException {
        try {
            DelegateTargetReport report = new DelegateTargetReport();

            report.setTargetId(rs.getInt("target_id"));
            report.setTargetName(rs.getString("target_name"));
            report.setTargetType(rs.getString("target_type"));
            report.setPeriodType(rs.getString("period_type"));

            if (rs.getDate("period_from") != null) {
                report.setPeriodFrom(rs.getDate("period_from").toLocalDate());
            }

            if (rs.getDate("period_to") != null) {
                report.setPeriodTo(rs.getDate("period_to").toLocalDate());
            }

            report.setDelegateId(rs.getInt("delegate_id"));
            report.setDelegateName(rs.getString("delegate_name"));
            report.setTargetAmount(rs.getDouble("target_amount"));
            report.setTargetQuantity(rs.getDouble("target_quantity"));
            report.setTargetCount(rs.getInt("target_count"));
            report.setAchievedValue(rs.getDouble("achieved_value"));
            report.setRequiredValue(rs.getDouble("required_value"));
            report.setAchievementPercent(rs.getDouble("achievement_percent"));
            report.setRemainingValue(rs.getDouble("remaining_value"));
            report.setAchievementStatus(rs.getString("achievement_status"));
            report.setTargetStatus(rs.getString("target_status"));
            report.setNotes(rs.getString("notes"));

            return report;
        } catch (SQLException e) {
            log.error("Error mapping DelegateTargetReport: {}", e.getMessage());
            throw new DaoException(e);
        }
    }

    @Override
    public Object[] getData(DelegatePerformanceReport report) {
        return new Object[]{
                report.getDelegateId(),
                report.getDelegateName(),
                report.getReportDate(),
                report.getInvoicesCount(),
                report.getCustomersCount(),
                report.getGrossSales(),
                report.getGrossReturns(),
                report.getNetSales(),
                report.getTotalCollected(),
                report.getNetProfit(),
                report.getProfitPercent()
        };
    }
}
