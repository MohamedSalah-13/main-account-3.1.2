package com.hamza.account.features.rbac;

/** Data-driven permission metadata used by the RBAC editor. */
public record RbacPermission(
        int id,
        String code,
        String description,
        String category,
        int sortOrder
) {
}
