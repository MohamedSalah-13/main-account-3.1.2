package com.hamza.account.authorization;

/** Metadata synchronized to the authorization catalogue at application startup. */
public record PermissionDefinition(
        PermissionKey key,
        String module,
        String resource,
        String action,
        PermissionRisk risk,
        int sortOrder
) {
}
