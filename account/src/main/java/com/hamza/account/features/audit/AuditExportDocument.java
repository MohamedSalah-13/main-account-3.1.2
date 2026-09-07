package com.hamza.account.features.audit;

import java.time.LocalDateTime;
import java.util.List;

public record AuditExportDocument(AuditLogQuery query,
                                  List<AuditLogEntry> rows,
                                  LocalDateTime generatedAt) {
    public AuditExportDocument {
        rows = List.copyOf(rows);
    }
}
