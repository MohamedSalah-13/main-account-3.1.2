package com.hamza.account.features.rbac;

/** Explicit per-user exception applied after inherited role permissions are resolved. */
public enum RbacOverrideEffect {
    ALLOW,
    DENY
}
