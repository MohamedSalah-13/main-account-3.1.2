package com.hamza.account.features.rbac;

/** A named bundle of permissions assignable to any number of users. */
public record RbacRole(
        int id,
        String code,
        String name,
        String description,
        boolean systemRole,
        boolean active
) {
    public String displayName() {
        return name + " (" + code + ")";
    }
}
