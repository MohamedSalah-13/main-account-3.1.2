package com.hamza.account.security.cache;

import com.hamza.account.type.PermissionCode;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cache للصلاحيات لتحسين الأداء وتقليل الاستعلامات من قاعدة البيانات
 */
@Log4j2
public class PermissionCache {

    private static final PermissionCache INSTANCE = new PermissionCache();
    
    // Cache لصلاحيات كل مستخدم
    private final Map<Integer, UserPermissionCache> userCaches = new ConcurrentHashMap<>();
    
    // مدة صلاحية الـ Cache (بالدقائق)
    private static final long CACHE_EXPIRY_MINUTES = 30;
    
    private PermissionCache() {
        // إنشاء Thread لتنظيف الـ Cache المنتهي
        startCacheCleanupThread();
    }

    public static PermissionCache getInstance() {
        return INSTANCE;
    }

    /**
     * الحصول على صلاحيات المستخدم من الـ Cache أو إنشاء cache جديد
     */
    public UserPermissionCache getUserCache(int userId) {
        return userCaches.computeIfAbsent(userId, id -> new UserPermissionCache(userId));
    }

    /**
     * التحقق من وجود صلاحية (مع استخدام Cache)
     */
    public Boolean hasPermission(int userId, PermissionCode permission) {
        UserPermissionCache cache = getUserCache(userId);
        return cache.hasPermission(permission);
    }

    /**
     * إضافة صلاحيات المستخدم إلى الـ Cache
     */
    public void cacheUserPermissions(int userId, Set<String> permissionCodes) {
        UserPermissionCache cache = getUserCache(userId);
        cache.setPermissions(permissionCodes);
    }

    /**
     * مسح cache مستخدم معين
     */
    public void invalidateUser(int userId) {
        userCaches.remove(userId);
        log.info("تم مسح cache الصلاحيات للمستخدم: {}", userId);
    }

    /**
     * مسح كل الـ Cache
     */
    public void invalidateAll() {
        userCaches.clear();
        log.info("تم مسح جميع cache الصلاحيات");
    }

    /**
     * مسح الـ Cache المنتهي
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        userCaches.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                log.debug("تم حذف cache منتهي للمستخدم: {}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * إحصائيات الـ Cache
     */
    public CacheStats getStats() {
        int totalUsers = userCaches.size();
        long totalPermissions = userCaches.values().stream()
                .mapToLong(UserPermissionCache::getPermissionCount)
                .sum();
        
        return new CacheStats(totalUsers, totalPermissions);
    }

    /**
     * بدء Thread لتنظيف الـ Cache المنتهي
     */
    private void startCacheCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.MINUTES.sleep(10); // كل 10 دقائق
                    cleanupExpired();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("PermissionCacheCleanup");
        cleanupThread.start();
    }

    /**
     * Cache خاص بمستخدم واحد
     */
    public static class UserPermissionCache {
        private final int userId;
        private Set<String> permissions;
        private long cacheTime;
        private final Map<String, Boolean> permissionChecks = new ConcurrentHashMap<>();

        public UserPermissionCache(int userId) {
            this.userId = userId;
            this.cacheTime = System.currentTimeMillis();
        }

        public Boolean hasPermission(PermissionCode permission) {
            if (isExpired()) {
                return null; // يحتاج إعادة تحميل
            }
            
            return permissionChecks.computeIfAbsent(permission.getCode(), code -> 
                permissions != null && permissions.contains(code)
            );
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
            this.cacheTime = System.currentTimeMillis();
            this.permissionChecks.clear(); // مسح الـ checks القديمة
        }

        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        public boolean isExpired(long currentTime) {
            long ageMinutes = TimeUnit.MILLISECONDS.toMinutes(currentTime - cacheTime);
            return ageMinutes >= CACHE_EXPIRY_MINUTES;
        }

        public long getPermissionCount() {
            return permissions != null ? permissions.size() : 0;
        }
    }

    /**
     * إحصائيات الـ Cache
     */
    public record CacheStats(int cachedUsers, long totalPermissions) {
    }
}
