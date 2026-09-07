package com.hamza.account.features.audit;

import java.nio.file.Path;
import java.util.Objects;

/** A completed sensitive audit operation, published only after its durable work succeeds. */
public sealed interface AuditOperationEvent {

    record Exported(AuditExportFormat format, Path target, int rows, boolean administration)
            implements AuditOperationEvent {
        public Exported {
            format = Objects.requireNonNull(format, "format");
            target = Objects.requireNonNull(target, "target");
            rows = Math.max(0, rows);
        }
    }

    record Deleted(int rows) implements AuditOperationEvent {
        public Deleted {
            rows = Math.max(0, rows);
        }
    }

    record RetentionPolicyChanged(boolean enabled, int days) implements AuditOperationEvent {
    }

    record RetentionCleanupCompleted(int rows, boolean automatic) implements AuditOperationEvent {
        public RetentionCleanupCompleted {
            rows = Math.max(0, rows);
        }
    }
}
