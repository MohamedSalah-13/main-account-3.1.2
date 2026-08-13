package com.hamza.account.authorization;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.jetbrains.annotations.NotNull;

/** Single authorization gateway used by both UI hints and service-layer enforcement. */
public final class AuthorizationGuard {

    private AuthorizationGuard() {
    }

    public static boolean isGranted(PermissionKey permission) {
        if (permission == null) return false;
        if (permission.isPublicMarker()) return true;
        if (permission.isDenyMarker()) return false;
        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        return session != null && session.hasPermission(permission);
    }

    public static void require(@NotNull PermissionKey permission) throws DaoException {
        if (permission == null) {
            throw new DaoException("تعريف الصلاحية مفقود؛ تم رفض العملية احترازيًا");
        }
        if (!isGranted(permission)) {
            throw new BusinessRuleException("ليس لديك صلاحية " + permission.value() + " لتنفيذ هذه العملية");
        }
    }
}
