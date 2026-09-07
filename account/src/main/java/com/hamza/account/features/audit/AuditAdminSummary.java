package com.hamza.account.features.audit;

/** Counts over the complete filtered administration history. */
public record AuditAdminSummary(long total, long exports, long deletions, long retentionChanges,
                                long cleanups) {
    public static final AuditAdminSummary EMPTY = new AuditAdminSummary(0, 0, 0, 0, 0);
}
