package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;

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
            throw new BusinessRuleException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }
        ShiftSummary summary = userShiftService.getCurrentShiftSummary(userId);
        return new ShiftReportData(shift, summary, LocalDateTime.now(), "X-Report");
    }

    /**
     * بيانات تقرير Z — بعد غلق الوردية (يُستدعى بـ shiftId).
     */
    public ShiftReportData buildZReport(int shiftId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().getDataById(shiftId);
        if (shift == null) {
            throw new BusinessRuleException("الوردية غير موجودة!");
        }
        double totalIn = shift.getTotalCashIn();
        double totalOut = shift.getTotalCashOut();

        // A shift closed before V22 has no in/out columns filled in: its expectation
        // was computed under the old four-source rule and stored, and it cannot be
        // recomputed - which till it was on was never recorded. Carrying the stored
        // figure across as one net movement reprints what the shift said on the day,
        // rather than collapsing it to the opening balance.
        if (totalIn == 0 && totalOut == 0 && shift.getExpectedBalance() != shift.getOpenBalance()) {
            double net = shift.getExpectedBalance() - shift.getOpenBalance();
            totalIn = net > 0 ? net : 0;
            totalOut = net < 0 ? -net : 0;
        }

        ShiftSummary summary = ShiftSummary.builder()
                .openBalance(shift.getOpenBalance())
                .totalSales(shift.getTotalSales())
                .totalSalesReturns(shift.getTotalSalesReturns())
                .totalExpenses(shift.getTotalExpenses())
                .totalDeposits(shift.getTotalDeposits())
                .totalWithdrawals(shift.getTotalWithdrawals())
                .otherIn(Math.max(0, totalIn - shift.getTotalSales() - shift.getTotalDeposits()))
                .otherOut(Math.max(0, totalOut - shift.getTotalSalesReturns()
                        - shift.getTotalExpenses() - shift.getTotalWithdrawals()))
                .totalIn(totalIn)
                .totalOut(totalOut)
                .invoicesCount(shift.getInvoicesCount())
                .build();
        return new ShiftReportData(shift, summary, shift.getCloseTime(), "Z-Report");
    }

    /**
     * تقرير تجميعي لورديات فترة زمنية معينة.
     * <p>
     * {@code userId} is nullable, and null means every user - so this returns the opening
     * cash, closing cash and difference of every cashier in a period. Nothing calls it
     * today, which is exactly why the guard goes on now: an unguarded method that leaks
     * other people's money figures is not safe because it is unreachable, it is a door
     * left open for whichever screen is wired to it first, by someone who will reasonably
     * assume the service already asked.
     * <p>
     * {@code USER_SHIFT_MANAGE} is the same key that {@code UserShiftService.getAllShifts}
     * requires, and it costs nothing here because there is no caller to break.
     */
    public List<UserShift> buildAggregateReport(LocalDateTime from, LocalDateTime to, Integer userId)
            throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
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
