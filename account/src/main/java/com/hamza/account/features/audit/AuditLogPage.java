package com.hamza.account.features.audit;

import java.util.List;

/** One server-side page and the matching totals produced from the same filter. */
public record AuditLogPage(List<AuditLogEntry> rows, AuditLogSummary summary) {

    public AuditLogPage {
        rows = List.copyOf(rows);
        summary = summary == null ? AuditLogSummary.EMPTY : summary;
    }

    public int pageCount(int pageSize) {
        return pageSize < 1 ? 1 : (int) Math.max(1, (summary.total() + pageSize - 1) / pageSize);
    }
}
