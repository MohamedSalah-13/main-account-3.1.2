package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/**
 * خدمة تجميع بيانات تقارير الورديات (X-Report / Z-Report / تقارير تجميعية).
 */
public record ShiftReportService(DaoFactory daoFactory, UserShiftService userShiftService) {

    /**
     * بيانات تقرير X (لحظي) — وردية مفتوحة.
     */
    public ShiftReportData buildXReport(int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_X_REPORT_VIEW);
        UserShift shift = userShiftService.getOpenShift(userId);
        if (shift == null) {
            throw new BusinessRuleException(message("user.shift.msg.no.open.shift"));
        }
        ShiftSummary summary = userShiftService.getCurrentShiftSummary(userId);
        return new ShiftReportData(shift, summary, LocalDateTime.now(), "X-Report");
    }

    /**
     * بيانات تقرير Z — بعد غلق الوردية (يُستدعى بـ shiftId).
     */
    public ShiftReportData buildZReport(int shiftId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_REPORT_REPRINT);
        return buildZReportInternal(shiftId, null);
    }

    public ShiftReportData buildOwnZReport(int shiftId, int userId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SHIFT_SELF_CLOSE);
        return buildZReportInternal(shiftId, userId);
    }

    private ShiftReportData buildZReportInternal(int shiftId, Integer expectedUserId) throws DaoException {
        UserShift shift = daoFactory.userShiftDao().getDataById(shiftId);
        if (shift == null) {
            throw new BusinessRuleException(message("user.shift.msg.not.found"));
        }
        if (expectedUserId != null && shift.getUserId() != expectedUserId) {
            throw new BusinessRuleException(message("user.shift.error.self.only"));
        }
        BigDecimal totalIn = shift.getTotalCashIn();
        BigDecimal totalOut = shift.getTotalCashOut();

        // A shift closed before V22 has no in/out columns filled in: its expectation
        // was computed under the old four-source rule and stored, and it cannot be
        // recomputed - which till it was on was never recorded. Carrying the stored
        // figure across as one net movement reprints what the shift said on the day,
        // rather than collapsing it to the opening balance.
        if (totalIn.signum() == 0 && totalOut.signum() == 0
                && shift.getExpectedBalance().compareTo(shift.getOpenBalance()) != 0) {
            BigDecimal net = shift.getExpectedBalance().subtract(shift.getOpenBalance());
            totalIn = net.signum() > 0 ? net : BigDecimal.ZERO;
            totalOut = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
        }

        ShiftSummary summary = ShiftSummary.builder()
                .openBalance(shift.getOpenBalance())
                .totalSales(shift.getTotalSales())
                .totalSalesReturns(shift.getTotalSalesReturns())
                .totalExpenses(shift.getTotalExpenses())
                .totalDeposits(shift.getTotalDeposits())
                .totalWithdrawals(shift.getTotalWithdrawals())
                .otherIn(totalIn.subtract(shift.getTotalSales()).subtract(shift.getTotalDeposits()).max(BigDecimal.ZERO))
                .otherOut(totalOut.subtract(shift.getTotalSalesReturns())
                        .subtract(shift.getTotalExpenses()).subtract(shift.getTotalWithdrawals()).max(BigDecimal.ZERO))
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

    private static String message(String key) {
        return com.hamza.controlsfx.language.LanguageManager.getInstance().getString(key);
    }
}
