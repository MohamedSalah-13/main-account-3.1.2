package com.hamza.account.features.audit;

import java.util.List;

public record AuditAdminOptions(List<AuditUserOption> users, List<String> eventTypes) {
    public AuditAdminOptions {
        users = List.copyOf(users);
        eventTypes = List.copyOf(eventTypes);
    }
}
