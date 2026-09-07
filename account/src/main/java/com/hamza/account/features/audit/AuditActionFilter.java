package com.hamza.account.features.audit;

/** Filter choices for the audit screen; {@link #ALL} deliberately has no SQL value. */
public enum AuditActionFilter {
    ALL(null, "audit.log.filter.all.actions"),
    INSERT(AuditAction.INSERT, "audit.log.action.insert"),
    UPDATE(AuditAction.UPDATE, "audit.log.action.update"),
    DELETE(AuditAction.DELETE, "audit.log.action.delete");

    private final AuditAction action;
    private final String labelKey;

    AuditActionFilter(AuditAction action, String labelKey) {
        this.action = action;
        this.labelKey = labelKey;
    }

    public AuditAction action() {
        return action;
    }

    public String labelKey() {
        return labelKey;
    }
}
