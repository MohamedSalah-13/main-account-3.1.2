package com.hamza.account.features.employee.payroll;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One month's payroll, as it stands.
 * <p>
 * The three "who and when" pairs are separate columns rather than an audit trail read out of
 * {@code audit_log}, because the status bar of the screen has to say who approved this run and
 * when without a second query, and because approval and payment are the two moments a person
 * is accountable for. The audit log still records them; this is the run's own memory of them.
 *
 * @param employeeCount how many lines it holds - read with the run so a list does not query per row
 * @param totalEarned   the sum of the lines' earnings
 * @param totalNet      the sum of the lines' net pay
 */
public record PayrollRun(int id,
                         PayrollPeriod period,
                         PayrollRunStatus status,
                         String notes,
                         LocalDateTime createdAt,
                         int createdBy,
                         String createdByName,
                         LocalDateTime approvedAt,
                         Integer approvedBy,
                         String approvedByName,
                         LocalDateTime paidAt,
                         Integer paidBy,
                         String paidByName,
                         int employeeCount,
                         BigDecimal totalEarned,
                         BigDecimal totalNet) {

    /** The single answer every disabled control on the screen hangs off. */
    public boolean isEditable() {
        return status.isEditable();
    }
}
