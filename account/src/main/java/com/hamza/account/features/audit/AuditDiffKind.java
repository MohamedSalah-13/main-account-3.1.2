package com.hamza.account.features.audit;

/** How one flattened JSON field changed between the stored snapshots. */
public enum AuditDiffKind {
    ADDED("audit.diff.kind.added"),
    REMOVED("audit.diff.kind.removed"),
    CHANGED("audit.diff.kind.changed"),
    UNCHANGED("audit.diff.kind.unchanged");

    private final String labelKey;

    AuditDiffKind(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
