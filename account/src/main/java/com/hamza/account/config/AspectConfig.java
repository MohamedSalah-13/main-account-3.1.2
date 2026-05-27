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

    /**
     * تهيئة Aspects
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // تفعيل PermissionAspect
            PermissionAspect aspect = Aspects.aspectOf(PermissionAspect.class);
            log.info("تم تفعيل PermissionAspect بنجاح");
            
            initialized = true;
        } catch (Exception e) {
            log.error("خطأ في تهيئة Aspects", e);
        }
    }
}
