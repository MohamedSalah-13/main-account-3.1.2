package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDateTime;
import java.util.List;

public record UserShiftService(DaoFactory daoFactory) {

    /**
     * فتح وردية جديدة للمستخدم
     */
    public int openShift(int userId, double openBalance, String notes) throws DaoException {
        // التحقق من عدم وجود وردية مفتوحة
        if (daoFactory.userShiftDao().hasOpenShift(userId)) {
            throw new DaoException("يوجد وردية مفتوحة بالفعل لهذا المستخدم!");
        }

        UserShift shift = new UserShift();
        shift.setUserId(userId);
        shift.setOpenTime(LocalDateTime.now());
        shift.setOpenBalance(openBalance);
        shift.setOpen(true);
        shift.setNotes(notes);

        return daoFactory.userShiftDao().insert(shift);
    }

    /**
     * غلق الوردية الحالية للمستخدم
     */
    public int closeShift(int userId, double closeBalance, String notes) throws DaoException {
        UserShift openShift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (openShift == null) {
            throw new DaoException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }

        openShift.setCloseTime(LocalDateTime.now());
        openShift.setCloseBalance(closeBalance);
        openShift.setOpen(false);
        if (notes != null && !notes.isEmpty()) {
            openShift.setNotes(notes);
        }

        return daoFactory.userShiftDao().update(openShift);
    }

    /**
     * الحصول على الوردية المفتوحة للمستخدم
     */
    public UserShift getOpenShift(int userId) throws DaoException {
        return daoFactory.userShiftDao().getOpenShiftByUserId(userId);
    }

    /**
     * التحقق من وجود وردية مفتوحة
     */
    public boolean hasOpenShift(int userId) throws DaoException {
        return daoFactory.userShiftDao().hasOpenShift(userId);
    }

    /**
     * الحصول على جميع ورديات المستخدم
     */
    public List<UserShift> getUserShifts(int userId) throws DaoException {
        return daoFactory.userShiftDao().getShiftsByUserId(userId);
    }

    /**
     * الحصول على جميع الورديات
     */
    public List<UserShift> getAllShifts() throws DaoException {
        return daoFactory.userShiftDao().loadAll();
    }

    /**
     * حذف وردية
     */
    public int deleteShift(int shiftId) throws DaoException {
        return daoFactory.userShiftDao().deleteById(shiftId);
    }
}
