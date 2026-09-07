package com.hamza.account.features.audit;

/** Where a recorded statement originated. */
public enum AuditSourceFilter {
    ALL(null, "audit.log.filter.all.sources"),
    APP("APP", "audit.log.source.app"),
    SYSTEM("SYSTEM", "audit.log.source.system"),
    DATABASE("DATABASE", "audit.log.source.database");

    private final String databaseValue;
    private final String labelKey;

    AuditSourceFilter(String databaseValue, String labelKey) {
        this.databaseValue = databaseValue;
        this.labelKey = labelKey;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public String labelKey() {
        return labelKey;
    }
}
