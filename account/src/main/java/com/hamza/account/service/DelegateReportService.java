package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.DelegatePerformanceReport;
import com.hamza.account.model.domain.DelegateTargetReport;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;

@Log4j2
public class DelegateReportService {

    private final DaoFactory daoFactory;

    public DelegateReportService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    public List<DelegatePerformanceReport> getDailyPerformanceReport(
            Integer delegateId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) throws DaoException {
        validateDates(dateFrom, dateTo);
        return daoFactory.delegateReportDao().performanceReport(normalizeId(delegateId), dateFrom, dateTo);
    }

    public List<DelegatePerformanceReport> getPerformanceSummary(
            Integer delegateId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) throws DaoException {
        validateDates(dateFrom, dateTo);
        return daoFactory.delegateReportDao().performanceSummary(normalizeId(delegateId), dateFrom, dateTo);
    }

    public List<DelegateTargetReport> getTargetReport(
            Integer delegateId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) throws DaoException {
        validateDates(dateFrom, dateTo);
        return daoFactory.delegateReportDao().targetReport(normalizeId(delegateId), dateFrom, dateTo);
    }

    public double sumGrossSales(List<DelegatePerformanceReport> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegatePerformanceReport::getGrossSales)
                .sum();
    }

    public double sumNetSales(List<DelegatePerformanceReport> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegatePerformanceReport::getNetSales)
                .sum();
    }

    public double sumTotalCollected(List<DelegatePerformanceReport> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegatePerformanceReport::getTotalCollected)
                .sum();
    }

    public double sumNetProfit(List<DelegatePerformanceReport> list) {
        return list == null ? 0 : list.stream()
                .mapToDouble(DelegatePerformanceReport::getNetProfit)
                .sum();
    }

    public int sumInvoicesCount(List<DelegatePerformanceReport> list) {
        return list == null ? 0 : list.stream()
                .mapToInt(DelegatePerformanceReport::getInvoicesCount)
                .sum();
    }

    public long countAchievedTargets(List<DelegateTargetReport> list) {
        return list == null ? 0 : list.stream()
                .filter(target -> "ACHIEVED".equalsIgnoreCase(target.getAchievementStatus()))
                .count();
    }

    public long countInProgressTargets(List<DelegateTargetReport> list) {
        return list == null ? 0 : list.stream()
                .filter(target -> "IN_PROGRESS".equalsIgnoreCase(target.getAchievementStatus()))
                .count();
    }

    public double averageAchievementPercent(List<DelegateTargetReport> list) {
        if (list == null || list.isEmpty()) {
            return 0;
        }

        return list.stream()
                .mapToDouble(DelegateTargetReport::getAchievementPercent)
                .average()
                .orElse(0);
    }

    private void validateDates(LocalDate dateFrom, LocalDate dateTo) throws DaoException {
        if (dateFrom == null) {
            throw new DaoException("من فضلك اختر تاريخ البداية");
        }

        if (dateTo == null) {
            throw new DaoException("من فضلك اختر تاريخ النهاية");
        }

        if (dateTo.isBefore(dateFrom)) {
            throw new DaoException("تاريخ النهاية يجب أن يكون أكبر من أو يساوي تاريخ البداية");
        }
    }

    private Integer normalizeId(Integer id) {
        return id == null || id <= 0 ? null : id;
    }
}
