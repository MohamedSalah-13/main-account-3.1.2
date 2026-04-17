package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDateTime;
import java.util.List;

public record UserShiftService(DaoFactory daoFactory) {

    public int openShift(int userId, double openBalance, String notes) throws DaoException {
        if (userId <= 0) {
            throw new DaoException("معرّف المستخدم غير صالح!");
        }
        if (openBalance < 0) {
            throw new DaoException("لا يمكن أن يكون الرصيد الافتتاحي بالسالب!");
        }
        if (daoFactory.userShiftDao().hasOpenShift(userId)) {
            throw new DaoException("يوجد وردية مفتوحة بالفعل لهذا المستخدم!");
        }

        UserShift shift = new UserShift(userId);
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
            throw new DaoException("لا يمكن أن يكون الرصيد الختامي بالسالب!");
        }

        UserShift openShift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (openShift == null) {
            throw new DaoException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }

        LocalDateTime closeTime = LocalDateTime.now();

        // حساب الملخص خلال الفترة
        ShiftSummary summary = daoFactory.userShiftDao().calculateShiftSummary(
                userId, openShift.getOpenTime(), closeTime);
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
            throw new DaoException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }
        ShiftSummary summary = daoFactory.userShiftDao()
                .calculateShiftSummary(userId, openShift.getOpenTime(), LocalDateTime.now());
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

    public List<UserShift> getAllShifts() throws DaoException {
        return daoFactory.userShiftDao().loadAll();
    }

    public int deleteShift(int shiftId) throws DaoException {
        return daoFactory.userShiftDao().deleteById(shiftId);
    }
}