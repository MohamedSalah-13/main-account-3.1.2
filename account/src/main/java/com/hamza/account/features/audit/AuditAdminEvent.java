package com.hamza.account.features.audit;

import java.time.LocalDateTime;

/** Immutable evidence for an operation performed on the audit trail itself. */
public record AuditAdminEvent(long id,
                              String eventType,
                              Integer actorUserId,
                              String actorName,
                              String source,
                              String workstationId,
                              String workstationName,
                              LocalDateTime occurredAt,
                              String reason,
                              long affectedRows,
                              String details) {
}
