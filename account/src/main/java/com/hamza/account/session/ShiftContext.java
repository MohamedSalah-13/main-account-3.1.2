package com.hamza.account.session;

import com.hamza.account.model.domain.UserShift;

/**
 * سياق الوردية الحالية للمستخدم خلال جلسة التطبيق.
 * يتم تحديثه عند فتح/غلق الوردية، ويُقرأ عند تنفيذ أي عملية مالية.
 *
 * الاستخدام:
 * <pre>
 *   if (!ShiftContext.isOpen()) {
 *       AllAlerts.alertError("افتح وردية أولاً!");
 *       return;
 *   }
 *   int shiftId = ShiftContext.getCurrentShiftId();
 * </pre>
 */
public final class ShiftContext {

    private static volatile UserShift currentShift;

    private ShiftContext() {}

    public static synchronized void setCurrentShift(UserShift shift) {
        currentShift = shift;
    }

    public static synchronized UserShift getCurrentShift() {
        return currentShift;
    }

    public static synchronized void clear() {
        currentShift = null;
    }

    public static boolean isOpen() {
        UserShift s = currentShift;
        return s != null && s.isOpen();
    }

    public static int getCurrentShiftId() {
        UserShift s = currentShift;
        return (s != null) ? s.getId() : 0;
    }
}