package com.hamza.account.features.audit;

/** Whitelisted orderings for the administration journal. */
public enum AuditAdminSort {
    NEWEST("audit.log.sort.newest", "e.occurred_at DESC, e.id DESC"),
    OLDEST("audit.log.sort.oldest", "e.occurred_at ASC, e.id ASC"),
    ACTOR("audit.log.sort.actor", "actor_display ASC, e.occurred_at DESC"),
    EVENT("audit.admin.sort.event", "e.event_type ASC, e.occurred_at DESC");

    private final String labelKey;
    private final String orderBy;

    AuditAdminSort(String labelKey, String orderBy) {
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
