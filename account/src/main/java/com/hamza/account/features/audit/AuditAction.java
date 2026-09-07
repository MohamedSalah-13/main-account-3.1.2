package com.hamza.account.features.audit;

/** The three database changes accepted by {@code audit_log.action_type}. */
public enum AuditAction {
    INSERT("audit.log.action.insert"),
    UPDATE("audit.log.action.update"),
    DELETE("audit.log.action.delete");

    private final String labelKey;

    AuditAction(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
