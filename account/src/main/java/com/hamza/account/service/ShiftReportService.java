package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * خدمة تجميع بيانات تقارير الورديات (X-Report / Z-Report / تقارير تجميعية).
 */
public record ShiftReportService(DaoFactory daoFactory, UserShiftService userShiftService) {

    /**
     * بيانات تقرير X (لحظي) — وردية مفتوحة.
     */
    public ShiftReportData buildXReport(int userId) throws DaoException {
        UserShift shift = userShiftService.getOpenShift(userId);
        if (shift == null) {
            throw new DaoException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }
        ShiftSummary summary = userShiftService.getCurrentShiftSummary(userId);
        return new ShiftReportData(shift, summary, LocalDateTime.now(), "X-Report");
    }

    /**
     * بيانات تقرير Z — بعد غلق الوردية (يُستدعى بـ shiftId).
     */
    public ShiftReportData buildZReport(int shiftId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().loadById(shiftId);
        if (shift == null) {
            throw new DaoException("الوردية غير موجودة!");
        }
        ShiftSummary summary = ShiftSummary.builder()
                .openBalance(shift.getOpenBalance())
                .totalSales(shift.getTotalSales())
                .totalSalesReturns(shift.getTotalSalesReturns())
                .totalExpenses(shift.getTotalExpenses())
                .totalDeposits(shift.getTotalDeposits())
                .totalWithdrawals(shift.getTotalWithdrawals())
                .invoicesCount(shift.getInvoicesCount())
                .build();
        return new ShiftReportData(shift, summary, shift.getCloseTime(), "Z-Report");
    }

    /**
     * تقرير تجميعي لورديات فترة زمنية معينة.
     */
    public List<UserShift> buildAggregateReport(LocalDateTime from, LocalDateTime to, Integer userId)
            throws DaoException {
        return daoFactory.userShiftDao().getShiftsBetween(from, to, userId);
    }

    /**
     * DTO موحّد للتقرير.
     */
    public record ShiftReportData(
            UserShift shift,
            ShiftSummary summary,
            LocalDateTime printTime,
            String reportType
    ) {
    }
}