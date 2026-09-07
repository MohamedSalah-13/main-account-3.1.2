package com.hamza.account.features.audit;

/** One readable field-level difference between an audit row's before and after JSON. */
public record AuditDiffRow(String field, String before, String after, AuditDiffKind kind) {
}
