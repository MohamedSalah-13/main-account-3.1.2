package com.hamza.account.session;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.alert.AllAlerts;

/**
 * سياق الوردية الحالية للمستخدم خلال جلسة التطبيق.
 * يتم تحديثه عند فتح/غلق الوردية، ويُقرأ عند تنفيذ أي عملية مالية.
 *
 * الاستخدام:
 * <pre>
 *   if (!ShiftContext.requireOpenShift()) return; // يعرض تنبيهاً ويوقف العملية
 *   // ... تابع الحفظ
 * </pre>
 */
public final class ShiftContext {

    /**
     * تفعيل/تعطيل الإلزام بوجود وردية مفتوحة قبل العمليات المالية.
     * في حال كان المستخدم لا يستخدم نظام الورديات بعد، يمكن تعطيله.
     * (مستقبلاً: يُقرأ من إعدادات النظام)
     */
    private static volatile boolean enforceShiftRequired = false;

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

    public static boolean isEnforceShiftRequired() {
        return enforceShiftRequired;
    }

    public static void setEnforceShiftRequired(boolean value) {
        enforceShiftRequired = value;
    }

    /**
     * الحارس المركزي: يتحقق من وجود وردية مفتوحة قبل السماح بعملية مالية.
     * <p>
     * - في حال عدم الإلزام (enforceShiftRequired=false) يعيد true دائماً.
     * - في حال عدم وجود وردية مفتوحة يعرض تنبيهاً باللغة العربية ويعيد false.
     *
     * @return true إذا يمكن المتابعة، false إذا يجب إيقاف العملية.
     */
    public static boolean requireOpenShift() {
        if (!enforceShiftRequired) return true;
        if (isOpen()) return true;
        AllAlerts.alertError(
                "لا يمكن إتمام العملية!\nيجب فتح وردية أولاً من شاشة إدارة الورديات.");
        return false;
    }
}