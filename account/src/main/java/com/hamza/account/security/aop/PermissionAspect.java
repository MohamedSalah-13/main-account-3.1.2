package com.hamza.account.security.aop;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.security.annotation.RequiresAllPermissions;
import com.hamza.account.security.annotation.RequiresAnyPermission;
import com.hamza.account.security.annotation.RequiresPermission;
import com.hamza.account.security.cache.PermissionCache;
import com.hamza.account.service.permission.AuthorizationService;
import com.hamza.account.type.PermissionCode;
import com.hamza.account.view.LogApplication;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * Aspect للتحقق من الصلاحيات تلقائياً عند استخدام Annotations
 */
@Aspect
@Log4j2
public class PermissionAspect {

    private final AuthorizationService authorizationService;
    private final PermissionCache permissionCache;

    public PermissionAspect() {
        this.authorizationService = ServiceRegistry.get(AuthorizationService.class);
        this.permissionCache = PermissionCache.getInstance();
    }

    /**
     * التحقق من صلاحية واحدة (@RequiresPermission)
     */
    @Around("@annotation(com.hamza.account.security.annotation.RequiresPermission)")
    public Object checkSinglePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethod(joinPoint);
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);

        int userId = LogApplication.usersVo.getId();
        PermissionCode permission = annotation.value();

        // التحقق من الـ Cache أولاً
        Boolean cachedResult = permissionCache.hasPermission(userId, permission);
        boolean hasPermission;

        if (cachedResult != null) {
            hasPermission = cachedResult;
            log.debug("استخدام Cache للصلاحية: {} للمستخدم: {}", permission.getCode(), userId);
        } else {
            // جلب من قاعدة البيانات
            hasPermission = authorizationService.hasPermission(userId, permission);
            log.debug("جلب من DB للصلاحية: {} للمستخدم: {}", permission.getCode(), userId);
        }

        if (!hasPermission) {
            return handlePermissionDenied(annotation.message(), permission, annotation.throwException());
        }

        log.debug("تم التحقق من الصلاحية: {} للمستخدم: {}", permission.getCode(), userId);
        return joinPoint.proceed();
    }

    /**
     * التحقق من أي صلاحية (@RequiresAnyPermission)
     */
    @Around("@annotation(com.hamza.account.security.annotation.RequiresAnyPermission)")
    public Object checkAnyPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethod(joinPoint);
        RequiresAnyPermission annotation = method.getAnnotation(RequiresAnyPermission.class);

        int userId = LogApplication.usersVo.getId();
        PermissionCode[] permissions = annotation.value();

        boolean hasAny = false;
        try {
            hasAny = authorizationService.hasAnyPermission(userId, permissions);
        } catch (DaoException e) {
            log.error("خطأ في التحقق من الصلاحيات", e);
        }

        if (!hasAny) {
            return handlePermissionDenied(
                    annotation.message(),
                    permissions[0], // نستخدم أول صلاحية في الرسالة
                    annotation.throwException()
            );
        }

        log.debug("تم التحقق من الصلاحيات (أي واحدة) للمستخدم: {}", userId);
        return joinPoint.proceed();
    }

    /**
     * التحقق من جميع الصلاحيات (@RequiresAllPermissions)
     */
    @Around("@annotation(com.hamza.account.security.annotation.RequiresAllPermissions)")
    public Object checkAllPermissions(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = getMethod(joinPoint);
        RequiresAllPermissions annotation = method.getAnnotation(RequiresAllPermissions.class);

        int userId = LogApplication.usersVo.getId();
        PermissionCode[] permissions = annotation.value();

        boolean hasAll = false;
        try {
            hasAll = authorizationService.hasAllPermissions(userId, permissions);
        } catch (DaoException e) {
            log.error("خطأ في التحقق من الصلاحيات", e);
        }

        if (!hasAll) {
            return handlePermissionDenied(
                    annotation.message(),
                    permissions[0],
                    annotation.throwException()
            );
        }

        log.debug("تم التحقق من جميع الصلاحيات للمستخدم: {}", userId);
        return joinPoint.proceed();
    }

    /**
     * معالجة رفض الصلاحية
     */
    private Object handlePermissionDenied(
            String customMessage,
            PermissionCode permission,
            boolean throwException
    ) throws DaoException {
        String message = customMessage.isEmpty()
                ? "ليس لديك صلاحية: " + permission.getTitleAr()
                : customMessage;

        log.warn("رفض الوصول: {} للصلاحية: {}",
                LogApplication.usersVo.getUsername(),
                permission.getCode());

        if (throwException) {
            throw new DaoException(message);
        } else {
            AllAlerts.alertError(message);
            return null;
        }
    }

    /**
     * الحصول على Method من JoinPoint
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }
}