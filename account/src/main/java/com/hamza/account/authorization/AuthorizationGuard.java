package com.hamza.account.authorization;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
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
            throw new DaoException(LanguageManager.getInstance().getString("auth.error.permission.undefined"));
        }
        if (!isGranted(permission)) {
            // The permission's name, as the roles screen shows it - not its key. The key put
            // "stock.count.create" in the middle of an Arabic sentence (seen on screen, 2026-09-21).
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("auth.error.permission.denied", PermissionLabels.describe(permission)));
        }
    }
}
