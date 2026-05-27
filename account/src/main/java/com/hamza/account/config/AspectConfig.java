package com.hamza.account.config;

import com.hamza.account.security.aop.PermissionAspect;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.Aspects;

/**
 * تكوين AspectJ للتطبيق
 */
@Log4j2
public class AspectConfig {

    private static boolean initialized = false;
    private static PermissionAspect permissionAspectInstance;

    /**
     * تهيئة Aspects
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // إنشاء instance من PermissionAspect يدوياً
            permissionAspectInstance = new PermissionAspect();
            log.info("تم تفعيل PermissionAspect بنجاح");

            initialized = true;
        } catch (Exception e) {
            log.error("خطأ في تهيئة Aspects", e);
        }
    }

    /**
     * الحصول على instance من PermissionAspect
     */
    public static PermissionAspect getPermissionAspect() {
        if (!initialized) {
            initialize();
        }
        return permissionAspectInstance;
    }
}
