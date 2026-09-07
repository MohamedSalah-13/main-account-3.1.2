package com.hamza.account.features.audit;

import java.time.LocalDateTime;
import java.util.Objects;

/** A detached audit row: no JavaFX state and no implicit current user. */
public record AuditLogEntry(long id,
                            String tableName,
                            String recordId,
                            AuditAction action,
                            Integer actorUserId,
                            String actorName,
                            LocalDateTime actionTime,
                            String oldData,
                            String newData,
                            String source,
                            String workstationId,
                            String workstationName,
                            String notes) {

    public AuditLogEntry {
        tableName = text(tableName);
        recordId = text(recordId);
        action = Objects.requireNonNull(action, "action");
        actionTime = Objects.requireNonNull(actionTime, "actionTime");
        actorName = text(actorName);
        oldData = text(oldData);
        newData = text(newData);
        source = text(source);
        workstationId = text(workstationId);
        workstationName = text(workstationName);
        notes = text(notes);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
