package com.hamza.account.features.audit;

/** A stable user id and its historical display name for the filter. */
public record AuditUserOption(Integer id, String name) {
    public static final AuditUserOption ALL = new AuditUserOption(null, "");

    public AuditUserOption {
        name = name == null ? "" : name;
    }

    public boolean isAll() {
        return id == null;
    }
}
