package com.hamza.account.features.employee;

/**
 * Which of the statement's two halves a row came from.
 * <p>
 * It is a column of {@code employee_account_table} rather than something inferred, and it is what
 * tells a reader which enum a row's kind belongs to - {@link EmployeeEntryKind} or
 * {@link EmployeeCashPurpose}. <b>Both carry a {@code BONUS}</b>, and they mean opposite things:
 * a bonus awarded increases what the business owes, a bonus paid reduces it. Resolving a kind
 * without its source would get the sign backwards on exactly that row.
 */
public enum EmployeeMovementSource {

    /** {@code employee_ledger}: what was earned, awarded or deducted without cash moving. */
    LEDGER("employee.source.ledger"),

    /** {@code expenses_details}: cash that left a till for this employee. Always a debit. */
    CASH("employee.source.cash");

    private final String messageKey;

    EmployeeMovementSource(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static EmployeeMovementSource of(String stored) {
        if (stored != null) {
            for (EmployeeMovementSource source : values()) {
                if (source.name().equals(stored)) {
                    return source;
                }
            }
        }
        throw new IllegalArgumentException("Unknown employee movement source: " + stored);
    }
}
