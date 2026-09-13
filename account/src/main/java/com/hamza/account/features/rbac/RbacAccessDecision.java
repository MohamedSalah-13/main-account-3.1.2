package com.hamza.account.features.rbac;

import java.util.Set;

/** Explains the final access decision and where it came from. */
public record RbacAccessDecision(
        RbacPermission permission,
        boolean granted,
        Set<String> roleSources,
        RbacUserOverride override
) {
    public RbacAccessDecision {
        roleSources = roleSources == null ? Set.of() : Set.copyOf(roleSources);
    }

    public String explanationKey() {
        if (override != null) {
            boolean hasReason = override.reason() != null && !override.reason().isBlank();
            if (override.effect() == RbacOverrideEffect.DENY) {
                return hasReason ? "user.rbac.access.source.override.deny.reason"
                        : "user.rbac.access.source.override.deny";
            }
            return hasReason ? "user.rbac.access.source.override.allow.reason"
                    : "user.rbac.access.source.override.allow";
        }
        return roleSources.isEmpty() ? "user.rbac.access.source.none"
                : "user.rbac.access.source.roles";
    }

    public Object[] explanationArguments() {
        if (override != null && override.reason() != null && !override.reason().isBlank()) {
            return new Object[]{override.reason()};
        }
        return roleSources.isEmpty() ? new Object[0] : new Object[]{String.join(", ", roleSources)};
    }
}
