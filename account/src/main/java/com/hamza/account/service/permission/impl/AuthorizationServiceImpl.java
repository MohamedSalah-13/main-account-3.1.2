package com.hamza.account.service.permission.impl;

import com.hamza.account.model.dao.permission.RolePermissionDao;
import com.hamza.account.model.dao.permission.UserPermissionDao;
import com.hamza.account.security.cache.PermissionCache;
import com.hamza.account.service.permission.AuthorizationService;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.database.DaoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.Set;

@Log4j2
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserPermissionDao userPermissionDao;
    private final RolePermissionDao rolePermissionDao;
    private final PermissionCache permissionCache = PermissionCache.getInstance();

    private static final int ADMIN_USER_ID = 1;

    @Override
    public boolean hasPermission(int userId, String permissionCode) throws DaoException {
        log.info("========== بدء التحقق من الصلاحية ==========");
        log.info("المستخدم ID: {}", userId);
        log.info("الصلاحية المطلوبة: {}", permissionCode);

        // ✅ Admin له كل الصلاحيات
        if (userId == ADMIN_USER_ID) {
            log.info("المستخدم {} هو Admin - منح الصلاحية تلقائياً", userId);
            return true;
        }

        // ✅ تجاهل الـ Cache مؤقتاً لضمان القراءة من قاعدة البيانات
        // التحقق من الـ Cache أولاً
        PermissionCode permission = PermissionCode.fromCode(permissionCode);
        Boolean cachedResult = null; // ✅ تعطيل الـ Cache مؤقتاً

        // if (permission != null) {
        //     cachedResult = permissionCache.hasPermission(userId, permission);
        //     if (cachedResult != null) {
        //         log.info("من Cache: الصلاحية {} = {}", permissionCode, cachedResult);
        //         return cachedResult;
        //     }
        // }

        // جلب من قاعدة البيانات
        log.info("جلب الصلاحيات من قاعدة البيانات...");

        Set<String> userPermissions = userPermissionDao.findGrantedPermissionCodesByUserId(userId);
        log.info("الصلاحيات المباشرة للمستخدم ({}): {}", userPermissions.size(), userPermissions);

        Set<String> rolePermissions = rolePermissionDao.findGrantedPermissionCodesByUserId(userId);
        log.info("الصلاحيات من الأدوار ({}): {}", rolePermissions.size(), rolePermissions);

        Set<String> allPermissions = new HashSet<>();
        allPermissions.addAll(userPermissions);
        allPermissions.addAll(rolePermissions);

        log.info("إجمالي الصلاحيات ({}): {}", allPermissions.size(), allPermissions);

        // حفظ في الـ Cache
        permissionCache.cacheUserPermissions(userId, allPermissions);

        boolean hasPermission = allPermissions.contains(permissionCode);
        log.info("النتيجة النهائية: المستخدم {} {} الصلاحية {}",
                userId,
                hasPermission ? "لديه" : "ليس لديه",
                permissionCode
        );
        log.info("========== انتهى التحقق من الصلاحية ==========\n");

        return hasPermission;
    }

    @Override
    public boolean hasPermission(int userId, PermissionCode permissionCode) throws DaoException {
        return hasPermission(userId, permissionCode.getCode());
    }

    @Override
    public boolean hasAnyPermission(int userId, PermissionCode... permissionCodes) throws DaoException {
        if (userId == ADMIN_USER_ID) {
            return true;
        }

        for (PermissionCode permissionCode : permissionCodes) {
            if (hasPermission(userId, permissionCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllPermissions(int userId, PermissionCode... permissionCodes) throws DaoException {
        if (userId == ADMIN_USER_ID) {
            return true;
        }

        for (PermissionCode permissionCode : permissionCodes) {
            if (!hasPermission(userId, permissionCode)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void requirePermission(int userId, PermissionCode permissionCode) throws DaoException {
        if (!hasPermission(userId, permissionCode)) {
            throw new DaoException("ليس لديك صلاحية: " + permissionCode.getTitleAr());
        }
    }
}