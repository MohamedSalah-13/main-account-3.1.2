package com.hamza.account.service;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDateTime;
import java.util.List;

public record UserShiftService(DaoFactory daoFactory) {

    /**
     * Opens a shift on one till.
     * <p>
     * The till is the new argument and the point of it: every figure the shift is
     * judged by is filtered by {@code treasury_id}, so a shift that does not name
     * one has no expected balance that means anything - see V22 and
     * {@code ShiftCashSummary}.
     */
    public int openShift(int userId, int treasuryId, double openBalance, String notes) throws DaoException {
        if (userId <= 0) {
            throw new UserValidationException("معرّف المستخدم غير صالح!");
        }
        if (treasuryId <= 0) {
            throw new UserValidationException(LanguageManager.getInstance().getString("expenses.error.select.treasury"));
        }
        if (openBalance < 0) {
            throw new UserValidationException("لا يمكن أن يكون الرصيد الافتتاحي بالسالب!");
        }
        if (daoFactory.userShiftDao().hasOpenShift(userId)) {
            throw new BusinessRuleException("يوجد وردية مفتوحة بالفعل لهذا المستخدم!");
        }

        UserShift shift = new UserShift(userId, treasuryId);
        shift.setOpenTime(LocalDateTime.now());
        shift.setOpenBalance(openBalance);
        shift.setOpen(true);
        shift.setNotes(notes);

        return daoFactory.userShiftDao().insert(shift);
    }

    /**
     * غلق الوردية مع حساب الملخص وتخزينه في السجل.
     */
    public int closeShift(int userId, double closeBalance, String notes) throws DaoException {
        if (closeBalance < 0) {
            throw new UserValidationException("لا يمكن أن يكون الرصيد الختامي بالسالب!");
        }

        UserShift openShift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (openShift == null) {
            throw new BusinessRuleException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }

        LocalDateTime closeTime = LocalDateTime.now();

        // حساب الملخص خلال الفترة
        ShiftSummary summary = daoFactory.userShiftDao().calculateShiftSummary(
                userId, openShift.getTreasuryId(), openShift.getOpenTime(), closeTime);
        summary.setOpenBalance(openShift.getOpenBalance());

        double expected = summary.getExpectedBalance();
        double diff = summary.calculateDifference(closeBalance);

        openShift.setCloseTime(closeTime);
        openShift.setCloseBalance(closeBalance);
        openShift.setOpen(false);
        openShift.setTotalSales(summary.getTotalSales());
        openShift.setTotalSalesReturns(summary.getTotalSalesReturns());
        openShift.setTotalExpenses(summary.getTotalExpenses());
        openShift.setTotalDeposits(summary.getTotalDeposits());
        openShift.setTotalWithdrawals(summary.getTotalWithdrawals());
        openShift.setTotalCashIn(summary.getTotalIn());
        openShift.setTotalCashOut(summary.getTotalOut());
        openShift.setExpectedBalance(expected);
        openShift.setDifference(diff);
        openShift.setInvoicesCount(summary.getInvoicesCount());

        if (notes != null && !notes.isBlank()) {
            String current = openShift.getNotes();
            openShift.setNotes((current == null || current.isBlank())
                    ? notes
                    : current + " | [غلق] " + notes);
        }

        return daoFactory.userShiftDao().update(openShift);
    }

    /**
     * ملخص لحظي للوردية المفتوحة (X-Report) — لا يغلقها.
     */
    public ShiftSummary getCurrentShiftSummary(int userId) throws DaoException {
        UserShift openShift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (openShift == null) {
            throw new BusinessRuleException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }
        ShiftSummary summary = daoFactory.userShiftDao()
                .calculateShiftSummary(userId, openShift.getTreasuryId(), openShift.getOpenTime(), LocalDateTime.now());
        summary.setOpenBalance(openShift.getOpenBalance());
        return summary;
    }

    public UserShift getOpenShift(int userId) throws DaoException {
        return daoFactory.userShiftDao().getOpenShiftByUserId(userId);
    }

    public boolean hasOpenShift(int userId) throws DaoException {
        return daoFactory.userShiftDao().hasOpenShift(userId);
    }

    public List<UserShift> getUserShifts(int userId) throws DaoException {
        return daoFactory.userShiftDao().getShiftsByUserId(userId);
    }

    /**
     * Every user's shifts, for the administrator screen - guarded, unlike
     * {@link #getUserShifts(int)}, which returns the caller their own.
     * <p>
     * A row here carries a cashier's opening and closing cash and the difference between
     * them: the number they are answerable for. Any signed-in user could read all of it
     * for everyone, because a read reaches no {@code require} and the architecture test
     * that guards write paths cannot see a read at all.
     * <p>
     * {@code USER_SHIFT_MANAGE} already exists and is already what deleting and
     * force-closing a shift require, so nothing is granted or revoked by this: whoever can
     * close someone else's shift can read it, and whoever cannot, cannot. That is why this
     * one is fixed here while {@code openShift} and {@code closeShift} are not - they would
     * need a key that no ordinary user holds yet.
     */
    public List<UserShift> getAllShifts() throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        return daoFactory.userShiftDao().loadAll();
    }

    public int deleteShift(int shiftId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        return daoFactory.userShiftDao().deleteById(shiftId);
    }

    public int forceCloseShift(int shiftId, double closeBalance, String notes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.USER_SHIFT_MANAGE);
        UserShift shift = daoFactory.userShiftDao().getDataById(shiftId);
        if (shift == null) {
            throw new BusinessRuleException("الوردية غير موجودة");
        }

        if (!shift.isOpen()) {
            throw new BusinessRuleException("الوردية مغلقة بالفعل");
        }

        shift.setCloseTime(java.time.LocalDateTime.now());
        shift.setCloseBalance(closeBalance);
        shift.setOpen(false);
        shift.setStatus("مغلقة قسريًا");

        if (notes != null && !notes.isBlank()) {
            String current = shift.getNotes();
            shift.setNotes((current == null || current.isBlank())
                    ? notes
                    : current + " | [Force Close] " + notes);
        }

        var summary = daoFactory.userShiftDao().calculateShiftSummary(
                shift.getUserId(),
                shift.getTreasuryId(),
                shift.getOpenTime(),
                shift.getCloseTime()
        );
        // The DAO answers movements only; the opening cash belongs to the shift and
        // has to be put back before anything reads getExpectedBalance(). Missing here
        // - and only here - a force-closed shift was reported short by exactly what
        // the cashier started the day with.
        summary.setOpenBalance(shift.getOpenBalance());

        shift.setTotalSales(summary.getTotalSales());
        shift.setTotalSalesReturns(summary.getTotalSalesReturns());
        shift.setTotalExpenses(summary.getTotalExpenses());
        shift.setTotalDeposits(summary.getTotalDeposits());
        shift.setTotalWithdrawals(summary.getTotalWithdrawals());
        shift.setTotalCashIn(summary.getTotalIn());
        shift.setTotalCashOut(summary.getTotalOut());
        shift.setInvoicesCount(summary.getInvoicesCount());
        shift.setExpectedBalance(summary.getExpectedBalance());
        shift.setDifference(summary.calculateDifference(closeBalance));

        return daoFactory.userShiftDao().update(shift);
    }
}
