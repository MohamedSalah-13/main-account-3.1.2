package com.hamza.account.features.audit;

import java.time.LocalDateTime;

/** Shop-wide audit retention policy. Disabled is the safe migration default. */
public record AuditRetentionPolicy(boolean enabled, int days, LocalDateTime lastRunAt) {
    public static final int DEFAULT_DAYS = 365;
    public static final int MIN_DAYS = 30;
    public static final int MAX_DAYS = 3650;

    public AuditRetentionPolicy {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("audit.log.retention.validation.days");
        }
    }

    public static AuditRetentionPolicy disabled() {
        return new AuditRetentionPolicy(false, DEFAULT_DAYS, null);
    }
}
