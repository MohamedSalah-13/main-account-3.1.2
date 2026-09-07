package com.hamza.account.features.audit;

import java.time.LocalDateTime;
import java.util.List;

public record AuditAdminExportDocument(AuditAdminEventQuery query,
                                       List<AuditAdminEvent> rows,
                                       LocalDateTime generatedAt) {
    public AuditAdminExportDocument {
        rows = List.copyOf(rows);
    }
}
