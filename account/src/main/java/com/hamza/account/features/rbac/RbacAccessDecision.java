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

    public String explanation() {
        if (override != null) {
            String prefix = override.effect() == RbacOverrideEffect.DENY
                    ? "مرفوض باستثناء فردي"
                    : "مسموح باستثناء فردي";
            return override.reason() == null || override.reason().isBlank()
                    ? prefix
                    : prefix + ": " + override.reason();
        }
        if (!roleSources.isEmpty()) return "من الدور: " + String.join("، ", roleSources);
        return "غير ممنوح";
    }
}
