package com.hamza.account.features.audit;

/** Whitelisted SQL orderings for the server-side audit list. */
public enum AuditLogSort {
    NEWEST("audit.log.sort.newest", "a.action_time DESC, a.id DESC"),
    OLDEST("audit.log.sort.oldest", "a.action_time ASC, a.id ASC"),
    ACTOR("audit.log.sort.actor", "actor_display ASC, a.action_time DESC"),
    TABLE("audit.log.sort.table", "a.table_name ASC, a.action_time DESC"),
    ACTION("audit.log.sort.action", "a.action_type ASC, a.action_time DESC");

    private final String labelKey;
    private final String orderBy;

    AuditLogSort(String labelKey, String orderBy) {
        this.labelKey = labelKey;
        this.orderBy = orderBy;
    }

    public String labelKey() {
        return labelKey;
    }

    String orderBy() {
        return orderBy;
    }
}
