package com.hamza.account.features.audit;

import java.util.List;

/** Distinct dimensions present in the log, loaded once when the screen opens. */
public record AuditLogOptions(List<AuditUserOption> users, List<String> tables) {
    public AuditLogOptions {
        users = List.copyOf(users);
        tables = List.copyOf(tables);
    }
}
