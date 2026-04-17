package com.hamza.account.session;

import com.hamza.account.model.domain.UserShift;
import com.hamza.account.service.UserShiftService;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

/**
 * مساعد لتهيئة سياق الوردية عند تسجيل الدخول.
 * استدعِ {@link #loadForUser} بعد نجاح الـ login.
 */
@Log4j2
public final class ShiftContextLoader {

    private ShiftContextLoader() {}

    public static void loadForUser(int userId, UserShiftService service) {
        try {
            if (service.hasOpenShift(userId)) {
                UserShift shift = service.getOpenShift(userId);
                ShiftContext.setCurrentShift(shift);
                log.info("Loaded open shift #{} for user #{}", shift.getId(), userId);
            } else {
                ShiftContext.clear();
                log.info("No open shift for user #{}", userId);
            }
        } catch (DaoException e) {
            log.error("Failed to load shift context for user #{}", userId, e);
            ShiftContext.clear();
        }
    }
}
