package com.hamza.account.features.rbac;

import java.time.LocalDateTime;

/** Auditable, optionally expiring permission exception for one user. */
public record RbacUserOverride(
        int userId,
        int permissionId,
        String permissionCode,
        String permissionDescription,
        RbacOverrideEffect effect,
        String reason,
        LocalDateTime expiresAt,
        int grantedBy,
        LocalDateTime grantedAt
) {
    public boolean isActiveAt(LocalDateTime time) {
        return expiresAt == null || expiresAt.isAfter(time);
    }
}
