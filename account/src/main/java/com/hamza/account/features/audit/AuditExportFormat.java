package com.hamza.account.features.audit;

public enum AuditExportFormat {
    EXCEL("xlsx", "audit.log.export.excel"),
    PDF("pdf", "audit.log.export.pdf");

    private final String extension;
    private final String labelKey;

    AuditExportFormat(String extension, String labelKey) {
        this.extension = extension;
        this.labelKey = labelKey;
    }

    public String extension() {
        return extension;
    }

    public String labelKey() {
        return labelKey;
    }
}
