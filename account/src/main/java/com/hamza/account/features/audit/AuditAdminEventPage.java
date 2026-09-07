package com.hamza.account.features.audit;

import java.util.List;

public record AuditAdminEventPage(List<AuditAdminEvent> rows, AuditAdminSummary summary) {
    public AuditAdminEventPage {
        rows = List.copyOf(rows);
        summary = summary == null ? AuditAdminSummary.EMPTY : summary;
    }

    public int pageCount(int pageSize) {
        return pageSize < 1 ? 1 : (int) Math.max(1, (summary.total() + pageSize - 1) / pageSize);
    }
}
