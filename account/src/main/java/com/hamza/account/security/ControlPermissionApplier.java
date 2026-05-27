package com.hamza.account.security;

import com.hamza.account.type.PermissionCode;
import javafx.scene.control.*;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

/**
 * Class متخصص لتطبيق الصلاحيات على مجموعة من Controls بطريقة سهلة
 */
@Log4j2
public class ControlPermissionApplier {

    private final Map<Control, PermissionConfig> controlsMap = new HashMap<>();

    /**
     * إضافة Control مع صلاحية وإجراء
     */
    public ControlPermissionApplier add(Control control, PermissionCode permission, Action action) {
        controlsMap.put(control, new PermissionConfig(permission, action));
        return this;
    }

    /**
     * إضافة مجموعة Controls بنفس الصلاحية والإجراء
     */
    public ControlPermissionApplier addAll(PermissionCode permission, Action action, Control... controls) {
        for (Control control : controls) {
            add(control, permission, action);
        }
        return this;
    }

    /**
     * تطبيق جميع الصلاحيات
     */
    public void apply() {
        controlsMap.forEach((control, config) -> {
            switch (config.action) {
                case DISABLE -> PermissionHelper.disableIfNotAllowed(control, config.permission);
                case HIDE -> PermissionHelper.hideIfNotAllowed(control, config.permission);
                case READ_ONLY -> {
                    if (control instanceof TextField tf) {
                        PermissionHelper.makeReadOnlyIfNotAllowed(tf, config.permission);
                    } else if (control instanceof TextArea ta) {
                        PermissionHelper.makeReadOnlyIfNotAllowed(ta, config.permission);
                    }
                }
            }
        });
    }

    /**
     * أنواع الإجراءات المتاحة
     */
    public enum Action {
        DISABLE,    // تعطيل
        HIDE,       // إخفاء
        READ_ONLY   // للقراءة فقط
    }

    /**
     * تكوين الصلاحية
     */
    private record PermissionConfig(PermissionCode permission, Action action) {
    }

    // =====================================================================
    // طرق مختصرة للاستخدام السريع
    // =====================================================================

    /**
     * إنشاء Applier جديد مع تعطيل Controls
     */
    public static ControlPermissionApplier forDisable() {
        return new ControlPermissionApplier();
    }

    /**
     * تطبيق سريع للتعطيل
     */
    public static void quickDisable(Map<Control, PermissionCode> controlPermissions) {
        controlPermissions.forEach(PermissionHelper::disableIfNotAllowed);
    }

    /**
     * تطبيق سريع للإخفاء
     */
    public static void quickHide(Map<Control, PermissionCode> controlPermissions) {
        controlPermissions.forEach(PermissionHelper::hideIfNotAllowed);
    }
}
