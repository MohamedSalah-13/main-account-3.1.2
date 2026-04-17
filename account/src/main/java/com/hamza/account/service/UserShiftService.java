package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDateTime;
import java.util.List;

public record UserShiftService(DaoFactory daoFactory) {

    /**
     * فتح وردية جديدة للمستخدم.
     */
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
     * غلق الوردية الحالية للمستخدم.
     */
    public int closeShift(int userId, double closeBalance, String notes) throws DaoException {
        if (closeBalance < 0) {
            throw new DaoException("لا يمكن أن يكون الرصيد الختامي بالسالب!");
        }

        UserShift openShift = daoFactory.userShiftDao().getOpenShiftByUserId(userId);
        if (openShift == null) {
            throw new DaoException("لا توجد وردية مفتوحة لهذا المستخدم!");
        }

        openShift.setCloseTime(LocalDateTime.now());
        openShift.setCloseBalance(closeBalance);
        openShift.setOpen(false);
        if (notes != null && !notes.isBlank()) {
            // دمج الملاحظات: إبقاء ملاحظات الفتح مع إضافة ملاحظات الغلق
            String current = openShift.getNotes();
            openShift.setNotes((current == null || current.isBlank())
                    ? notes
                    : current + " | [غلق] " + notes);
        }

        return daoFactory.userShiftDao().update(openShift);
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