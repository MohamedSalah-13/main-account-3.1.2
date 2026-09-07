package com.hamza.account.features.audit;

/** Counts over the complete filtered result, never only the visible page. */
public record AuditLogSummary(long total, long inserts, long updates, long deletes) {
    public static final AuditLogSummary EMPTY = new AuditLogSummary(0, 0, 0, 0);
}
