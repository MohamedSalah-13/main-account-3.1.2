package com.hamza.account.security.cache;

import lombok.extern.log4j.Log4j2;

/**
 * Manager للتحكم في Permission Cache
 */
@Log4j2
public class PermissionCacheManager {

    private static final PermissionCache cache = PermissionCache.getInstance();

    /**
     * مسح cache مستخدم عند تغيير صلاحياته
     */
    public static void invalidateUserCache(int userId) {
        cache.invalidateUser(userId);
        log.info("تم تحديث cache الصلاحيات للمستخدم: {}", userId);
    }

    /**
     * مسح cache جميع المستخدمين (عند تغيير صلاحيات دور مثلاً)
     */
    public static void invalidateAllCache() {
        cache.invalidateAll();
        log.info("تم تحديث cache الصلاحيات لجميع المستخدمين");
    }

    /**
     * إحصائيات الـ Cache
     */
    public static PermissionCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    /**
     * تنظيف الـ Cache المنتهي يدوياً
     */
    public static void cleanupExpired() {
        cache.cleanupExpired();
    }

    /**
     * طباعة إحصائيات الـ Cache
     */
    public static void printStats() {
        PermissionCache.CacheStats stats = getCacheStats();
        log.info("Permission Cache Stats:");
        log.info("  - Cached Users: {}", stats.cachedUsers());
        log.info("  - Total Permissions: {}", stats.totalPermissions());
    }
}
