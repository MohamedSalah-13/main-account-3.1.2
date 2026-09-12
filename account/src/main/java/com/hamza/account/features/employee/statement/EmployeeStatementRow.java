package com.hamza.account.features.employee.statement;

import com.hamza.account.features.employee.EmployeeCashPurpose;
import com.hamza.account.features.employee.EmployeeEntryKind;
import com.hamza.account.features.employee.EmployeeMovementSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One movement on an employee's account, from either of the statement's two halves.
 *
 * @param kindCode       the stored code, not a label: {@code ENTITLEMENT}, {@code ADVANCE},
 *                       {@code DEDUCTION}. Which enum it belongs to is {@link #source}'s answer -
 *                       both carry a {@code BONUS} and they mean opposite things
 * @param runningBalance what the business owed this employee after this movement, accumulated in
 *                       SQL over the whole period and seeded with what came before it. Not
 *                       recomputed in Java over what happens to be on screen: a running total
 *                       restarted at zero is how a statement for September was printed as though
 *                       the employee began September owed nothing
 */
public record EmployeeStatementRow(LocalDate date,
                                   EmployeeMovementSource source,
                                   String kindCode,
                                   int sourceId,
                                   BigDecimal debit,
                                   BigDecimal credit,
                                   String notes,
                                   LocalDateTime enteredAt,
                                   int userId,
                                   String userName,
                                   BigDecimal runningBalance) {

    public EmployeeStatementRow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(source, "source");
        debit = debit == null ? BigDecimal.ZERO : debit;
        credit = credit == null ? BigDecimal.ZERO : credit;
    }

    /**
     * The key the screen translates for this row's kind.
     * <p>
     * Resolved through the source, because the two enums overlap: a {@code BONUS} from the ledger
     * is one awarded and a {@code BONUS} in cash is one handed over. An unknown code is shown as
     * itself rather than throwing - a statement of three hundred movements must not fail to draw
     * because one row carries a code a newer build writes.
     */
    public String kindMessageKey() {
        try {
            return source == EmployeeMovementSource.CASH
                    ? EmployeeCashPurpose.of(kindCode).messageKey()
                    : EmployeeEntryKind.of(kindCode).messageKey();
        } catch (IllegalArgumentException unknown) {
            return kindCode;
        }
    }

    /** The figure without its direction - exactly one of the two columns is ever non-zero. */
    public BigDecimal amount() {
        return debit.add(credit);
    }

    /** Whether this row is money that actually left a till. */
    public boolean isCash() {
        return source == EmployeeMovementSource.CASH;
    }
}
